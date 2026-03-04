package com.mustafa.service.impl;

import com.mustafa.dto.request.OpenAccountRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.AccountResponse;
import com.mustafa.dto.response.TransactionResponse;
import com.mustafa.dto.response.UserProfileResponse;
import com.mustafa.entity.Account;
import com.mustafa.entity.AppUser;
import com.mustafa.entity.Company;
import com.mustafa.entity.RetailCustomer;
import com.mustafa.entity.Transaction;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.AccountRepository;
import com.mustafa.repository.AppUserRepository;
import com.mustafa.repository.CompanyRepository;
import com.mustafa.repository.RetailCustomerRepository;
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

    private final AppUserRepository appUserRepository;
    private final RetailCustomerRepository retailCustomerRepository;
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    // Ortak Yardımcı Metot: Şirket veya Bireyin ismini bulur
    private String getOwnerName(AppUser appUser) {
        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            return retailCustomerRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber())
                    .map(r -> r.getFirstName() + " " + r.getLastName()).orElse("Bilinmeyen Birey");
        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            return companyRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber())
                    .map(Company::getCompanyName).orElse("Bilinmeyen Şirket");
        }
        return "Sistem Yöneticisi";
    }

    // Ortak Yardımcı Metot: Email adresini bulur
    private String getOwnerEmail(AppUser appUser) {
        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            return retailCustomerRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber())
                    .map(RetailCustomer::getEmail).orElse("");
        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            return companyRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber())
                    .map(Company::getContactEmail).orElse("");
        }
        return "admin@bank.com";
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(account -> AccountResponse.builder()
                        .id(account.getId())
                        .accountNumber(account.getAccountNumber())
                        .iban(account.getIban())
                        .balance(account.getBalance())
                        .currency(account.getCurrency())
                        .isActive(account.isActive())
                        .ownerName(getOwnerName(account.getAppUser())) // 🚀 YENİ DTO ALANI
                        .identityNumber(account.getAppUser().getIdentityNumber()) // 🚀 YENİ DTO ALANI
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getCustomerAccounts(String identityNumber) {
        AppUser appUser = appUserRepository.findByIdentityNumber(identityNumber)
                .orElseThrow(() -> new BankOperationException("Kullanıcı bulunamadı!"));

        return appUser.getAccounts().stream()
                .map(account -> AccountResponse.builder()
                        .id(account.getId())
                        .accountNumber(account.getAccountNumber())
                        .iban(account.getIban())
                        .balance(account.getBalance())
                        .currency(account.getCurrency())
                        .isActive(account.isActive())
                        .ownerName(getOwnerName(appUser))
                        .identityNumber(appUser.getIdentityNumber())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<UserProfileResponse> getAllCustomers() {
        return appUserRepository.findAll().stream()
                .map(appUser -> UserProfileResponse.builder()
                        .identityNumber(appUser.getIdentityNumber())
                        .profileName(getOwnerName(appUser))
                        .email(getOwnerEmail(appUser))
                        .role(appUser.getRole().name())
                        .status(appUser.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteCustomer(String identityNumber) {
        AppUser appUser = appUserRepository.findByIdentityNumber(identityNumber)
                .orElseThrow(() -> new BankOperationException("Silinecek kullanıcı bulunamadı!"));
        appUserRepository.delete(appUser); // Cascade sayesinde bağlı profil ve hesaplar da silinir
    }

    @Override
    @Transactional
    public UserProfileResponse updateCustomer(String identityNumber, UpdateProfileRequest request) {
        AppUser appUser = appUserRepository.findByIdentityNumber(identityNumber)
                .orElseThrow(() -> new BankOperationException("Güncellenecek kullanıcı bulunamadı!"));

        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            RetailCustomer retail = retailCustomerRepository.findByAppUser_IdentityNumber(identityNumber).get();

            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                retail.setEmail(request.getEmail());
            }

            // 🚀 DÜZELTME: Admin güncellerken de Ad ve Soyad ayrımını kusursuz yapıyoruz
            if (request.getProfileName() != null && !request.getProfileName().isBlank()) {
                String fullName = request.getProfileName().trim();
                int lastSpaceIndex = fullName.lastIndexOf(" ");

                if (lastSpaceIndex == -1) {
                    // Sadece tek bir isim girildiyse
                    retail.setFirstName(fullName);
                    retail.setLastName(""); // Eski soyadı mutlaka siliyoruz!
                } else {
                    // Son boşluğa kadar olan kısım Ad, sonrası Soyad
                    retail.setFirstName(fullName.substring(0, lastSpaceIndex).trim());
                    retail.setLastName(fullName.substring(lastSpaceIndex + 1).trim());
                }
            }
            retailCustomerRepository.save(retail);

        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            Company company = companyRepository.findByAppUser_IdentityNumber(identityNumber).get();

            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                company.setContactEmail(request.getEmail());
            }

            if (request.getProfileName() != null && !request.getProfileName().isBlank()) {
                company.setCompanyName(request.getProfileName().trim());
            }
            companyRepository.save(company);
        }

        // getOwnerName ve getOwnerEmail metotların güncel veriyi veritabanından doğru şekilde alacaktır
        return UserProfileResponse.builder()
                .identityNumber(appUser.getIdentityNumber())
                .profileName(getOwnerName(appUser))
                .email(getOwnerEmail(appUser))
                .role(appUser.getRole().name())
                .status(appUser.getStatus().name())
                .build();
    }

    @Override
    public List<TransactionResponse> getAccountTransactions(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        List<Transaction> transactions = transactionRepository
                .findBySenderAccountIdOrReceiverAccountIdOrderByTransactionDateDesc(account.getId(), account.getId());

        return transactions.stream().map(transaction -> TransactionResponse.builder()
                .referenceNo(transaction.getReferenceNo())
                .amount(transaction.getAmount())
                .convertedAmount(transaction.getConvertedAmount() != null ? transaction.getConvertedAmount() : transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .senderAccountId(transaction.getSenderAccount() != null ? transaction.getSenderAccount().getId() : null)
                .receiverAccountId(transaction.getReceiverAccount() != null ? transaction.getReceiverAccount().getId() : null)
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AccountResponse openAccountForCustomer(String identityNumber, OpenAccountRequest request) {
        AppUser appUser = appUserRepository.findByIdentityNumber(identityNumber)
                .orElseThrow(() -> new BankOperationException("Kullanıcı bulunamadı!"));

        Account.Currency accountCurrency;
        try {
            if (request.getCurrency() == null || request.getCurrency().isBlank()) throw new IllegalArgumentException();
            accountCurrency = Account.Currency.valueOf(request.getCurrency().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BankOperationException("Geçersiz para birimi! Sadece TRY, USD veya EUR desteklenmektedir.");
        }

        String generatedAccountNumber = String.valueOf((long) (Math.random() * 9000000000L) + 1000000000L);
        String generatedIban = "TR" + "00000" + generatedAccountNumber + "000000001";

        Account newAccount = Account.builder()
                .appUser(appUser)
                .accountNumber(generatedAccountNumber)
                .iban(generatedIban)
                .balance(java.math.BigDecimal.ZERO)
                .isActive(true)
                .currency(accountCurrency)
                .build();

        Account savedAccount = accountRepository.save(newAccount);

        return AccountResponse.builder()
                .id(savedAccount.getId())
                .accountNumber(savedAccount.getAccountNumber())
                .iban(savedAccount.getIban())
                .balance(savedAccount.getBalance())
                .currency(savedAccount.getCurrency())
                .isActive(savedAccount.isActive())
                .ownerName(getOwnerName(appUser))
                .identityNumber(appUser.getIdentityNumber())
                .build();
    }

    @Transactional
    @Override
    public void updateCustomerStatus(String identityNumber, String status) {
        AppUser appUser = appUserRepository.findByIdentityNumber(identityNumber)
                .orElseThrow(() -> new BankOperationException("Kullanıcı bulunamadı!"));

        appUser.setStatus(AppUser.ApprovalStatus.valueOf(status.toUpperCase()));
        appUserRepository.save(appUser);
    }
}