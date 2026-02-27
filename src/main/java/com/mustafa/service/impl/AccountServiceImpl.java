package com.mustafa.service.impl;

import com.mustafa.dto.request.CreateAccountRequest;
import com.mustafa.dto.response.AccountResponse;
import com.mustafa.entity.Account;
import com.mustafa.entity.Customer;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.AccountRepository;
import com.mustafa.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

    // Ortak Metot: Token'dan o an giriş yapmış müşteriyi bulur
    private Customer getAuthenticatedCustomer() {
        // JwtAuthenticationFilter'da kaydettiğimiz TC No'yu buradan çekiyoruz
        String tcNo = SecurityContextHolder.getContext().getAuthentication().getName();
        return customerRepository.findByTcNo(tcNo)
                .orElseThrow(() -> new RuntimeException("Yetkili müşteri bulunamadı!"));
    }

    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {
        // 1. İsteği yapan müşteriyi Token'dan bul
        Customer currentCustomer = getAuthenticatedCustomer();

        // 🚀 KRİTİK GÜVENLİK KONTROLÜ (Sadece ONAYLI olanlar geçebilir)
        if (currentCustomer.getStatus() != com.mustafa.entity.Customer.ApprovalStatus.APPROVED) {
            throw new BankOperationException("Hesabınız onaylı olmadığı için yeni banka hesabı açamazsınız. Lütfen durumunuzu kontrol ediniz.");
        }

        // 2. Benzersiz bir Hesap No ve IBAN üret (Veritabanında çakışma olmamasını garanti altına al)
        String accountNumber;
        String iban;
        do {
            accountNumber = AccountUtils.generateAccountNumber();
            iban = AccountUtils.generateIban(accountNumber);
        } while (accountRepository.existsByAccountNumber(accountNumber) || accountRepository.existsByIban(iban));

        // 3. Yeni Hesap nesnesini oluştur
        Account newAccount = Account.builder()
                .customer(currentCustomer) // Hesabın sahibi belli oldu!
                .currency(request.getCurrency()) // Müşteri TRY mi, USD mi istedi?
                .accountNumber(accountNumber)
                .iban(iban)
                .balance(BigDecimal.ZERO) // Yeni hesap her zaman 0 bakiye ile başlar
                .build();

        // 4. Veritabanına kaydet
        Account savedAccount = accountRepository.save(newAccount);

        // 5. DTO'ya çevirip geri dön
        return mapToResponse(savedAccount);
    }

    @Override
    public List<AccountResponse> getMyAccounts() {
        // 1. İsteği yapan müşteriyi bul
        Customer currentCustomer = getAuthenticatedCustomer();

        // 2. Bu müşterinin ID'sine ait tüm hesapları repository'den çek
        List<Account> accounts = accountRepository.findByCustomerId(currentCustomer.getId());

        // 3. Gelen Account listesini AccountResponse (DTO) listesine çevir
        return accounts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AccountResponse getAccountByAccountNumber(String accountNumber) {

        // 1. İsteği yapan yetkili müşteriyi bul (Zaten bu metodu yazmıştık)
        Customer currentCustomer = getAuthenticatedCustomer();

        // 2. Hesap numarasından hesabı veritabanında ara
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        // 3. GÜVENLİK KONTROLÜ: Bu hesap gerçekten giriş yapan müşteriye mi ait?
        if (!account.getCustomer().getId().equals(currentCustomer.getId())) {
            throw new BankOperationException("Sadece kendi hesap detaylarınızı görüntüleyebilirsiniz!");
        }

        // 4. Hesabı dışarıya açılabilir güvenli DTO (AccountResponse) formatına çevir
        return mapToResponse(account);
    }

    @Override
    @Transactional
    public void deleteAccount(String accountNumber) {
        // 1. Hesabı bul
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankOperationException("Hesap bulunamadı!"));

        // 2. Hesap zaten kapalıysa hata fırlat
        if (!account.isActive()) {
            throw new BankOperationException("Bu hesap zaten kapatılmış!");
        }

        // 3. Bakiye varsa kapatılamaz
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new BankOperationException("İçerisinde bakiye bulunan hesap kapatılamaz. Lütfen önce bakiyenizi transfer edin.");
        }

        // 4. GÜVENLİK VE YETKİ KONTROLÜ
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("ADMIN"));

        // Eğer işlem yapan kişi ADMIN DEĞİLSE;
        if (!isAdmin) {
            Customer currentCustomer = getAuthenticatedCustomer();

            // A) Sahiplik Kontrolü: Bu hesap kendisine mi ait?
            if (!account.getCustomer().getId().equals(currentCustomer.getId())) {
                throw new BankOperationException("Sadece kendi hesaplarınızı kapatabilirsiniz!");
            }

            // B) Onay Kontrolü (Sadece ONAYLI olanlar hesap silebilir)
            if (currentCustomer.getStatus() != Customer.ApprovalStatus.APPROVED) {
                throw new BankOperationException("Hesabınız onaylı olmadığı için hesap kapatma işlemi gerçekleştiremezsiniz. Lütfen durumunuzu kontrol ediniz.");
            }
        }

        // 5. SOFT DELETE (Eğer yukarıdaki kontrollerden geçerse -Adminse veya Şartları Sağlayan Kullanıcıysa-)
        account.setActive(false);
        accountRepository.save(account);
    }

    // Entity'den DTO'ya dönüştüren yardımcı metot (Kod tekrarını önler)
    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .iban(account.getIban())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .isActive(account.isActive())
                .build();
    }
}