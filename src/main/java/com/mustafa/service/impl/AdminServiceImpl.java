package com.mustafa.service.impl;

import com.mustafa.dto.request.OpenAccountRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.AccountResponse;
import com.mustafa.dto.response.CustomerResponse;
import com.mustafa.dto.response.TransactionResponse;
import com.mustafa.entity.Account;
import com.mustafa.entity.Customer;
import com.mustafa.entity.Transaction;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.AccountRepository;
import com.mustafa.repository.CustomerRepository;
import com.mustafa.repository.TransactionRepository;
import com.mustafa.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        // Veritabanındaki tüm hesapları tek seferde çekiyoruz
        return accountRepository.findAll().stream()
                .map(account -> AccountResponse.builder()
                        .id(account.getId())
                        .accountNumber(account.getAccountNumber())
                        .iban(account.getIban())
                        .balance(account.getBalance())
                        .currency(account.getCurrency())
                        .isActive(account.isActive())

                        // 🚀 YENİ EKLENEN: Hesap sahibinin adını ve TC'sini DTO'ya basıyoruz
                        .customerName(account.getCustomer() != null ? account.getCustomer().getFullName() : "Bilinmeyen Müşteri")
                        .customerTcNo(account.getCustomer() != null ? account.getCustomer().getTcNo() : "-")

                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getCustomerAccounts(String tcNo) {
        Customer customer = customerRepository.findByTcNo(tcNo)
                .orElseThrow(() -> new BankOperationException("Müşteri bulunamadı!"));

        return customer.getAccounts().stream()
                .map(account -> AccountResponse.builder()
                        .id(account.getId())
                        .accountNumber(account.getAccountNumber())
                        .iban(account.getIban())
                        .balance(account.getBalance())
                        .currency(account.getCurrency())
                        .isActive(account.isActive())

                        // 🚀 YENİ EKLENEN: Burada da müşteri adını ve TC'sini veriyoruz
                        .customerName(customer.getFullName())
                        .customerTcNo(customer.getTcNo())

                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(customer -> CustomerResponse.builder()
                        .tcNo(customer.getTcNo())
                        .fullName(customer.getFullName())
                        .email(customer.getEmail())
                        .role(customer.getRole().name())
                        .status(customer.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteCustomer(String tcNo) {
        Customer customer = customerRepository.findByTcNo(tcNo)
                .orElseThrow(() -> new BankOperationException("Silinecek müşteri bulunamadı!"));
        customerRepository.delete(customer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(String tcNo, UpdateProfileRequest request) {
        Customer customer = customerRepository.findByTcNo(tcNo)
                .orElseThrow(() -> new BankOperationException("Güncellenecek müşteri bulunamadı!"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            customer.setEmail(request.getEmail());
        }
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            customer.setFullName(request.getFullName());
        }

        customerRepository.save(customer);

        return CustomerResponse.builder()
                .tcNo(customer.getTcNo())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .role(customer.getRole().name())
                .status(customer.getStatus().name())
                .build();
    }

    @Override
    public List<TransactionResponse> getAccountTransactions(String accountNumber) {
        // 1. Hesabı bul
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        List<Transaction> transactions = transactionRepository
                .findBySenderAccountIdOrReceiverAccountIdOrderByTransactionDateDesc(account.getId(), account.getId());

        // 3. DTO'ya çevirip yolla
        return transactions.stream().map(transaction -> TransactionResponse.builder()
                .referenceNo(transaction.getReferenceNo())
                .amount(transaction.getAmount())
                .convertedAmount(transaction.getConvertedAmount() != null ? transaction.getConvertedAmount() : transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .senderAccountId(transaction.getSenderAccount() != null ? transaction.getSenderAccount().getId() : null)
                .receiverAccountId(transaction.getReceiverAccount() != null ? transaction.getReceiverAccount().getId() : null)
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AccountResponse openAccountForCustomer(String tcNo, OpenAccountRequest request) { // Parametre değişti
        // 1. Müşteriyi bul
        Customer customer = customerRepository.findByTcNo(tcNo)
                .orElseThrow(() -> new BankOperationException("Müşteri bulunamadı!"));

        // 2. Döviz Tipini Güvenli Bir Şekilde Enum'a Çevir (Exception Yönetimi)
        Account.Currency accountCurrency;
        try {
            // Eğer boş gelirse varsayılan olarak TRY de yapabilirsin veya hata fırlatırsın
            if (request.getCurrency() == null || request.getCurrency().isBlank()) {
                throw new IllegalArgumentException();
            }
            accountCurrency = Account.Currency.valueOf(request.getCurrency().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 🚀 Adam "JPY" veya saçma bir şey yazarsa buraya düşer
            throw new BankOperationException("Geçersiz para birimi! Sadece TRY, USD veya EUR desteklenmektedir.");
        }

        // 3. Rastgele Hesap No ve IBAN Üretimi
        String generatedAccountNumber = String.valueOf((long) (Math.random() * 9000000000L) + 1000000000L);
        String generatedIban = "TR" + "00000" + generatedAccountNumber + "000000001";

        // 4. Hesap Nesnesini Oluştur
        Account newAccount = new Account();
        newAccount.setCustomer(customer);
        newAccount.setAccountNumber(generatedAccountNumber);
        newAccount.setIban(generatedIban);
        newAccount.setBalance(java.math.BigDecimal.ZERO);
        newAccount.setActive(true);
        newAccount.setCurrency(accountCurrency); // Güvenli Enum'ı set ettik

        // 5. Veritabanına Kaydet
        Account savedAccount = accountRepository.save(newAccount);

        // 6. Ekrana DTO Dön
        return AccountResponse.builder()
                .id(savedAccount.getId())
                .accountNumber(savedAccount.getAccountNumber())
                .iban(savedAccount.getIban())
                .balance(savedAccount.getBalance())
                .currency(savedAccount.getCurrency())
                .isActive(savedAccount.isActive())
                .customerName(customer.getFullName())
                .customerTcNo(customer.getTcNo())
                .build();
    }

    // 🚀 YENİ: Adminin müşteri durumunu güncellemesi
    @Transactional
    @Override
    public void updateCustomerStatus(String tcNo, String status) {
        Customer customer = customerRepository.findByTcNo(tcNo)
                .orElseThrow(() -> new BankOperationException("Müşteri bulunamadı!"));

        // Gelen String'i (APPROVED, REJECTED) Enum'a çevirip kaydediyoruz
        customer.setStatus(Customer.ApprovalStatus.valueOf(status.toUpperCase()));
        customerRepository.save(customer);
    }
}