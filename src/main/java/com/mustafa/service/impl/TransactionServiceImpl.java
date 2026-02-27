package com.mustafa.service.impl;

import com.mustafa.dto.request.DepositRequest;
import com.mustafa.dto.request.TransferRequest;
import com.mustafa.dto.response.TransactionResponse;
import com.mustafa.entity.Account;
import com.mustafa.entity.Transaction;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.AccountRepository;
import com.mustafa.repository.TransactionRepository;
import com.mustafa.service.CurrencyService;
import com.mustafa.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CurrencyService currencyService;

    // Yüklü işlem (MASAK) sınırı
    private static final BigDecimal TRANSACTION_LIMIT = new BigDecimal("50000");

    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request) {

        Account account = accountRepository.findByIban(request.getIban())
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        String currentTcNo = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!account.getCustomer().getTcNo().equals(currentTcNo)) {
            throw new BankOperationException("Sadece kendi hesaplarınıza para yatırabilirsiniz!");
        }

        if (account.getCustomer().getStatus() != com.mustafa.entity.Customer.ApprovalStatus.APPROVED) {
            throw new BankOperationException("Hesabınız onaylı olmadığı için para yatırma işlemi yapamazsınız. Lütfen durumunuzu kontrol ediniz.");
        }

        if (!account.isActive()) {
            throw new BankOperationException("Bu hesap kapalı olduğu için para yatırma işlemi yapılamaz!");
        }

        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .referenceNo(UUID.randomUUID().toString())
                .receiverAccount(account)
                .amount(request.getAmount())
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .status(Transaction.TransactionStatus.COMPLETED) // Direkt onaylı
                .description("Kendi Hesabına Para Yatırma")
                .build();

        transactionRepository.save(transaction);
        return mapToResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {

        Account senderAccount = accountRepository.findByIban(request.getSenderIban())
                .orElseThrow(() -> new BankOperationException("Gönderen hesap bulunamadı!"));

        Account receiverAccount = accountRepository.findByIban(request.getReceiverIban())
                .orElseThrow(() -> new BankOperationException("Alıcı hesap bulunamadı!"));

        String currentTcNo = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        if (!senderAccount.getCustomer().getTcNo().equals(currentTcNo)) {
            throw new BankOperationException("Sadece kendi hesaplarınızdan para transferi yapabilirsiniz!");
        }

        if (senderAccount.getCustomer().getStatus() != com.mustafa.entity.Customer.ApprovalStatus.APPROVED) {
            throw new BankOperationException("Hesabınız onaylı olmadığı için para transferi gerçekleştiremezsiniz. Lütfen durumunuzu kontrol ediniz.");
        }

        if (senderAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BankOperationException("Yetersiz bakiye! İşlem gerçekleştirilemedi.");
        }

        if (senderAccount.getIban().equals(receiverAccount.getIban())) {
            throw new BankOperationException("Aynı hesaba transfer yapamazsınız. Lütfen farklı bir alıcı IBAN giriniz.");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankOperationException("Transfer tutarı 0'dan büyük olmalıdır!");
        }

        if (!senderAccount.isActive() || !receiverAccount.isActive()) {
            throw new BankOperationException("İşlem yapılacak hesaplardan biri kapalıdır!");
        }

        // 1. Gönderenden parayı DÜŞ (Para hesaptan her türlü çıkar, bloke olur)
        senderAccount.setBalance(senderAccount.getBalance().subtract(request.getAmount()));

        // 2. Canlı Kur Çevirimi (Admin onayladığında alıcıya ne kadar ekleneceğini şimdiden hesaplıyoruz)
        Double convertedAmountDouble = currencyService.convertAmount(
                request.getAmount().doubleValue(),
                senderAccount.getCurrency().toString(),
                receiverAccount.getCurrency().toString()
        );
        BigDecimal convertedAmount = BigDecimal.valueOf(convertedAmountDouble);

        // ... Üst kısımlar aynı (Canlı Kur Çevirimi vb.) ...

        String enrichedDescription = request.getDescription() +
                " (Çevrim: " + request.getAmount() + " " + senderAccount.getCurrency() +
                " -> " + String.format("%.2f", convertedAmountDouble) + " " + receiverAccount.getCurrency() + ")";

        // 🚀 3. MASAK KONTROLÜ (GÜNCELLENDİ: Çoklu Para Birimi Desteği)
        Transaction.TransactionStatus status;

        // İşlem tutarının o anki kurdan TRY (TL) karşılığını buluyoruz!
        Double amountInTryDouble = currencyService.convertAmount(
                request.getAmount().doubleValue(),
                senderAccount.getCurrency().toString(),
                "TRY"
        );
        BigDecimal amountInTry = BigDecimal.valueOf(amountInTryDouble);

        // Artık kıyaslamayı gönderilen orijinal tutarla değil, TL karşılığıyla (amountInTry) yapıyoruz.
        if (amountInTry.compareTo(TRANSACTION_LIMIT) >= 0) {
            // TL karşılığı 50.000 ve üstüyse: Alıcıya EKLEME, Onaya Gönder
            accountRepository.save(senderAccount);
            status = Transaction.TransactionStatus.PENDING_APPROVAL;

            // Açıklamaya kur bilgisini de ekleyelim ki admin neyi onayladığını bilsin
            enrichedDescription += String.format(" - [YÜKLÜ İŞLEM: Yaklaşık %.2f TL - YÖNETİCİ ONAYI BEKLİYOR]", amountInTryDouble);
        } else {
            // TL karşılığı 50.000 altındaysa: Alıcıya EKLE ve direkt Onayla
            receiverAccount.setBalance(receiverAccount.getBalance().add(convertedAmount));
            accountRepository.save(senderAccount);
            accountRepository.save(receiverAccount);
            status = Transaction.TransactionStatus.COMPLETED;
        }

        // 4. İşlemi Kaydet
        Transaction transaction = Transaction.builder()
                .referenceNo(UUID.randomUUID().toString())
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(request.getAmount())
                .convertedAmount(convertedAmount)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .status(status) // 🚀 Belirlenen statüyü atadık
                .description(enrichedDescription)
                .build();

        transactionRepository.save(transaction);

        return mapToResponse(transaction);
    }

    @Override
    public List<TransactionResponse> getAccountTransactions(String accountNumber, String type, String startDate, String endDate) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        String currentTcNo = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!account.getCustomer().getTcNo().equals(currentTcNo)) {
            throw new BankOperationException("Sadece kendi hesaplarınızın hareketlerini görebilirsiniz!");
        }

        List<Transaction> transactions = transactionRepository
                .findBySenderAccountIdOrReceiverAccountIdOrderByTransactionDateDesc(account.getId(), account.getId());

        Stream<Transaction> stream = transactions.stream();

        if (type != null && !type.isBlank()) {
            stream = stream.filter(t -> t.getTransactionType().name().equalsIgnoreCase(type));
        }

        if (startDate != null && !startDate.isBlank()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            stream = stream.filter(t -> !t.getTransactionDate().isBefore(start));
        }

        if (endDate != null && !endDate.isBlank()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
            stream = stream.filter(t -> !t.getTransactionDate().isAfter(end));
        }

        return stream.map(this::mapToResponse).collect(Collectors.toList());
    }

    // --- YENİ EKLENEN ADMİN METOTLARI ---

    @Override
    public List<TransactionResponse> getAllTransactionsForAdmin(String status) {
        Stream<Transaction> stream = transactionRepository.findAll().stream()
                .sorted((t1, t2) -> t2.getTransactionDate().compareTo(t1.getTransactionDate()));

        if (status != null && !status.isBlank()) {
            stream = stream.filter(t -> t.getStatus().name().equalsIgnoreCase(status));
        }

        return stream.map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TransactionResponse approveTransaction(String referenceNo) {
        Transaction transaction = transactionRepository.findByReferenceNo(referenceNo)
                .orElseThrow(() -> new BankOperationException("İşlem bulunamadı!"));

        if (transaction.getStatus() != Transaction.TransactionStatus.PENDING_APPROVAL) {
            throw new BankOperationException("Bu işlem onay bekleyen statüde değil!");
        }

        // Parayı alıcıya geçiriyoruz
        Account receiver = transaction.getReceiverAccount();
        receiver.setBalance(receiver.getBalance().add(transaction.getConvertedAmount()));
        accountRepository.save(receiver);

        // Statüyü ve açıklamayı güncelle
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        String updatedDesc = transaction.getDescription().replace(" - [YÜKLÜ İŞLEM: YÖNETİCİ ONAYI BEKLİYOR]", "") + " - [ONAYLANDI]";
        transaction.setDescription(updatedDesc);

        transactionRepository.save(transaction);
        return mapToResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse rejectTransaction(String referenceNo) {
        Transaction transaction = transactionRepository.findByReferenceNo(referenceNo)
                .orElseThrow(() -> new BankOperationException("İşlem bulunamadı!"));

        if (transaction.getStatus() != Transaction.TransactionStatus.PENDING_APPROVAL) {
            throw new BankOperationException("Bu işlem onay bekleyen statüde değil!");
        }

        // İADE İŞLEMİ: Bloke edilen parayı gönderene geri ver
        Account sender = transaction.getSenderAccount();
        sender.setBalance(sender.getBalance().add(transaction.getAmount()));
        accountRepository.save(sender);

        // Statüyü ve açıklamayı güncelle
        transaction.setStatus(Transaction.TransactionStatus.REJECTED);
        String updatedDesc = transaction.getDescription().replace(" - [YÜKLÜ İŞLEM: YÖNETİCİ ONAYI BEKLİYOR]", "") + " - [REDDEDİLDİ VE İADE EDİLDİ]";
        transaction.setDescription(updatedDesc);

        transactionRepository.save(transaction);
        return mapToResponse(transaction);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .referenceNo(transaction.getReferenceNo())
                .amount(transaction.getAmount())
                .convertedAmount(transaction.getConvertedAmount() != null ? transaction.getConvertedAmount() : transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .status(transaction.getStatus()) // 🚀 DTO'YA STATÜYÜ BAĞLADIK
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .senderAccountId(transaction.getSenderAccount() != null
                        ? transaction.getSenderAccount().getId()
                        : null)
                .receiverAccountId(transaction.getReceiverAccount() != null
                        ? transaction.getReceiverAccount().getId()
                        : null)
                .build();
    }
}