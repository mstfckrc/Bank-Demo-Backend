package com.mustafa.service.impl;

import com.mustafa.dto.request.DepositRequest;
import com.mustafa.dto.request.TransferRequest;
import com.mustafa.dto.response.TransactionResponse;
import com.mustafa.entity.Account;
import com.mustafa.entity.AppUser;
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

        String currentIdentity = SecurityContextHolder.getContext().getAuthentication().getName();

        // 🚀 CUSTOMER YERİNE APPUSER (KİMLİK) KONTROLÜ
        if (!account.getAppUser().getIdentityNumber().equals(currentIdentity)) {
            throw new BankOperationException("Sadece kendi hesaplarınıza para yatırabilirsiniz!");
        }

        // 🚀 CUSTOMER YERİNE APPUSER (ONAY) KONTROLÜ
        if (account.getAppUser().getStatus() != AppUser.ApprovalStatus.APPROVED) {
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
                .status(Transaction.TransactionStatus.COMPLETED)
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

        String currentIdentity = SecurityContextHolder.getContext().getAuthentication().getName();

        // 🚀 CUSTOMER YERİNE APPUSER KONTROLLERİ
        if (!senderAccount.getAppUser().getIdentityNumber().equals(currentIdentity)) {
            throw new BankOperationException("Sadece kendi hesaplarınızdan para transferi yapabilirsiniz!");
        }

        if (senderAccount.getAppUser().getStatus() != AppUser.ApprovalStatus.APPROVED) {
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

        // 1. Gönderenden parayı DÜŞ
        senderAccount.setBalance(senderAccount.getBalance().subtract(request.getAmount()));

        // 2. Canlı Kur Çevirimi
        Double convertedAmountDouble = currencyService.convertAmount(
                request.getAmount().doubleValue(),
                senderAccount.getCurrency().toString(),
                receiverAccount.getCurrency().toString()
        );
        BigDecimal convertedAmount = BigDecimal.valueOf(convertedAmountDouble);

        String enrichedDescription = request.getDescription() +
                " (Çevrim: " + request.getAmount() + " " + senderAccount.getCurrency() +
                " -> " + String.format("%.2f", convertedAmountDouble) + " " + receiverAccount.getCurrency() + ")";

        // 3. MASAK KONTROLÜ (Aynen Korundu)
        Transaction.TransactionStatus status;
        Double amountInTryDouble = currencyService.convertAmount(
                request.getAmount().doubleValue(),
                senderAccount.getCurrency().toString(),
                "TRY"
        );
        BigDecimal amountInTry = BigDecimal.valueOf(amountInTryDouble);

        if (amountInTry.compareTo(TRANSACTION_LIMIT) >= 0) {
            accountRepository.save(senderAccount);
            status = Transaction.TransactionStatus.PENDING_APPROVAL;
            enrichedDescription += String.format(" - [YÜKLÜ İŞLEM: Yaklaşık %.2f TL - YÖNETİCİ ONAYI BEKLİYOR]", amountInTryDouble);
        } else {
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
                .status(status)
                .description(enrichedDescription)
                .build();

        transactionRepository.save(transaction);
        return mapToResponse(transaction);
    }

    @Override
    public List<TransactionResponse> getAccountTransactions(String accountNumber, String type, String startDate, String endDate) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        String currentIdentity = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!account.getAppUser().getIdentityNumber().equals(currentIdentity)) {
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

    // --- ADMİN METOTLARI (Değişiklik yok, doğrudan Transaction ve Account üzerinden çalışıyor) ---

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
        Account receiver = transaction.getReceiverAccount();
        receiver.setBalance(receiver.getBalance().add(transaction.getConvertedAmount()));
        accountRepository.save(receiver);
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
        Account sender = transaction.getSenderAccount();
        sender.setBalance(sender.getBalance().add(transaction.getAmount()));
        accountRepository.save(sender);
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
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .senderAccountId(transaction.getSenderAccount() != null ? transaction.getSenderAccount().getId() : null)
                .receiverAccountId(transaction.getReceiverAccount() != null ? transaction.getReceiverAccount().getId() : null)
                .build();
    }
}