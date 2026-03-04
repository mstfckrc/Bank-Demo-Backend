package com.mustafa.service.impl;

import com.mustafa.dto.request.CreateAccountRequest;
import com.mustafa.dto.response.AccountResponse;
import com.mustafa.entity.Account;
import com.mustafa.entity.AppUser;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.AccountRepository;
import com.mustafa.repository.AppUserRepository;
import com.mustafa.repository.CompanyRepository;
import com.mustafa.repository.RetailCustomerRepository;
import com.mustafa.service.AccountService;
import com.mustafa.util.AccountUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AppUserRepository appUserRepository;
    private final RetailCustomerRepository retailCustomerRepository;
    private final CompanyRepository companyRepository;

    // Ortak Metot: Token'dan o an giriş yapmış kullanıcıyı bulur
    private AppUser getAuthenticatedAppUser() {
        String identityNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        return appUserRepository.findByIdentityNumber(identityNumber)
                .orElseThrow(() -> new RuntimeException("Yetkili kullanıcı bulunamadı!"));
    }

    // Ortak Metot: Şirket veya Birey olmasına göre ismini bulur (DTO için)
    private String getOwnerName(AppUser appUser) {
        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            return retailCustomerRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber())
                    .map(r -> r.getFirstName() + " " + r.getLastName()).orElse("Bilinmeyen Birey");
        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            return companyRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber())
                    .map(c -> c.getCompanyName()).orElse("Bilinmeyen Şirket");
        }
        return "Sistem Yöneticisi";
    }

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        AppUser currentUser = getAuthenticatedAppUser();

        if (currentUser.getStatus() != AppUser.ApprovalStatus.APPROVED) {
            throw new BankOperationException("Hesabınız onaylı olmadığı için yeni banka hesabı açamazsınız.");
        }

        String accountNumber;
        String iban;
        do {
            accountNumber = AccountUtils.generateAccountNumber();
            iban = AccountUtils.generateIban(accountNumber);
        } while (accountRepository.existsByAccountNumber(accountNumber) || accountRepository.existsByIban(iban));

        Account newAccount = Account.builder()
                .appUser(currentUser)
                .currency(request.getCurrency())
                .accountNumber(accountNumber)
                .iban(iban)
                .balance(BigDecimal.ZERO)
                .isActive(true)
                .build();

        Account savedAccount = accountRepository.save(newAccount);
        return mapToResponse(savedAccount);
    }

    @Override
    public List<AccountResponse> getMyAccounts() {
        AppUser currentUser = getAuthenticatedAppUser();
        List<Account> accounts = accountRepository.findByAppUserId(currentUser.getId());
        return accounts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public AccountResponse getAccountByAccountNumber(String accountNumber) {
        AppUser currentUser = getAuthenticatedAppUser();
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        if (!account.getAppUser().getId().equals(currentUser.getId())) {
            throw new BankOperationException("Sadece kendi hesap detaylarınızı görüntüleyebilirsiniz!");
        }
        return mapToResponse(account);
    }

    @Override
    @Transactional
    public void deleteAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        if (!account.isActive()) {
            throw new BankOperationException("Bu hesap zaten kapatılmış!");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new BankOperationException("İçerisinde bakiye bulunan hesap kapatılamaz.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("ADMIN"));

        if (!isAdmin) {
            AppUser currentUser = getAuthenticatedAppUser();
            if (!account.getAppUser().getId().equals(currentUser.getId())) {
                throw new BankOperationException("Sadece kendi hesaplarınızı kapatabilirsiniz!");
            }
            if (currentUser.getStatus() != AppUser.ApprovalStatus.APPROVED) {
                throw new BankOperationException("Hesabınız onaylı olmadığı için hesap kapatma işlemi gerçekleştiremezsiniz.");
            }
        }

        account.setActive(false);
        accountRepository.save(account);
    }

    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .iban(account.getIban())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .isActive(account.isActive())
                .ownerName(getOwnerName(account.getAppUser())) // DTO'ya dinamik isim basar
                .identityNumber(account.getAppUser().getIdentityNumber())
                .build();
    }
}