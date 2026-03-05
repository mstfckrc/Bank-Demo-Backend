package com.mustafa.service.impl;

import com.mustafa.dto.request.HireEmployeeRequest;
import com.mustafa.dto.request.SalaryPaymentRequest;
import com.mustafa.dto.request.TransferRequest;
import com.mustafa.dto.request.UpdateEmployeeRequest;
import com.mustafa.dto.response.CompanyEmployeeResponse;
import com.mustafa.dto.response.TransactionResponse;
import com.mustafa.entity.Account;
import com.mustafa.entity.Company;
import com.mustafa.entity.CompanyEmployee;
import com.mustafa.entity.RetailCustomer;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.*;
import com.mustafa.service.CompanyEmployeeService;
import com.mustafa.service.CurrencyService;
import com.mustafa.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyEmployeeServiceImpl implements CompanyEmployeeService {

    private final CompanyEmployeeRepository companyEmployeeRepository;
    private final CompanyRepository companyRepository;
    private final RetailCustomerRepository retailCustomerRepository;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final CurrencyService currencyService;

    @Override
    @Transactional
    public CompanyEmployeeResponse hireEmployee(String managerIdentityNumber, HireEmployeeRequest request) {
        Company company = companyRepository.findByAppUser_IdentityNumber(managerIdentityNumber)
                .orElseThrow(() -> new BankOperationException("Kurumsal profil bulunamadı!"));

        RetailCustomer employee = retailCustomerRepository.findByAppUser_IdentityNumber(request.getIdentityNumber())
                .orElseThrow(() -> new BankOperationException("Personel sisteme kayıtlı değil! Önce bireysel müşteri hesabı açmalıdır."));

        boolean exists = companyEmployeeRepository.existsByCompany_IdAndRetailCustomer_AppUser_IdentityNumber(
                company.getId(), request.getIdentityNumber());
        if (exists) {
            throw new BankOperationException("Bu personel zaten şirketinizde kayıtlı!");
        }

        Account account = accountRepository.findByIban(request.getSalaryIban())
                .orElseThrow(() -> new BankOperationException("Girilen IBAN sistemimizde bulunamadı!"));

        if (!account.getAppUser().getIdentityNumber().equals(request.getIdentityNumber())) {
            throw new BankOperationException("Güvenlik İhlali: Girilen IBAN personelin kendisine ait değil!");
        }
        if (!account.isActive()) {
            throw new BankOperationException("Girilen IBAN'a ait hesap pasif (kapatılmış) durumda!");
        }

        CompanyEmployee newEmployee = new CompanyEmployee();
        newEmployee.setCompany(company);
        newEmployee.setRetailCustomer(employee);
        newEmployee.setSalaryAmount(request.getSalaryAmount());
        newEmployee.setSalaryIban(request.getSalaryIban());

        CompanyEmployee savedEmployee = companyEmployeeRepository.save(newEmployee);
        return mapToResponse(savedEmployee);
    }

    @Override
    public List<CompanyEmployeeResponse> getMyEmployees(String managerIdentityNumber) {
        Company company = companyRepository.findByAppUser_IdentityNumber(managerIdentityNumber)
                .orElseThrow(() -> new BankOperationException("Kurumsal profil bulunamadı!"));

        List<CompanyEmployee> employees = companyEmployeeRepository.findByCompanyId(company.getId());

        return employees.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CompanyEmployeeResponse updateEmployee(String managerIdentityNumber, String employeeIdentityNumber, UpdateEmployeeRequest request) {
        Company company = companyRepository.findByAppUser_IdentityNumber(managerIdentityNumber)
                .orElseThrow(() -> new BankOperationException("Kurumsal profil bulunamadı!"));

        CompanyEmployee employeeRecord = companyEmployeeRepository
                .findByCompany_IdAndRetailCustomer_AppUser_IdentityNumber(company.getId(), employeeIdentityNumber)
                .orElseThrow(() -> new BankOperationException("Bu TC numarasına ait bir çalışanınız bulunamadı!"));

        if (!employeeRecord.getSalaryIban().equals(request.getSalaryIban())) {
            Account account = accountRepository.findByIban(request.getSalaryIban())
                    .orElseThrow(() -> new BankOperationException("Girilen yeni IBAN sistemimizde bulunamadı!"));

            if (!account.getAppUser().getIdentityNumber().equals(employeeIdentityNumber)) {
                throw new BankOperationException("Güvenlik İhlali: Yeni IBAN personelin kendisine ait değil!");
            }
            if (!account.isActive()) {
                throw new BankOperationException("Girilen IBAN'a ait hesap pasif (kapatılmış) durumda!");
            }
            employeeRecord.setSalaryIban(request.getSalaryIban());
        }

        employeeRecord.setSalaryAmount(request.getSalaryAmount());
        CompanyEmployee updatedEmployee = companyEmployeeRepository.save(employeeRecord);

        return mapToResponse(updatedEmployee);
    }

    @Override
    @Transactional
    public void removeEmployee(String managerIdentityNumber, String employeeIdentityNumber) {
        Company company = companyRepository.findByAppUser_IdentityNumber(managerIdentityNumber)
                .orElseThrow(() -> new BankOperationException("Kurumsal profil bulunamadı!"));

        CompanyEmployee employeeRecord = companyEmployeeRepository
                .findByCompany_IdAndRetailCustomer_AppUser_IdentityNumber(company.getId(), employeeIdentityNumber)
                .orElseThrow(() -> new BankOperationException("Bu TC numarasına ait bir çalışanınız bulunamadı!"));

        companyEmployeeRepository.delete(employeeRecord);
    }

    @Override
    @Transactional
    public List<TransactionResponse> paySalaries(String managerIdentityNumber, SalaryPaymentRequest request) {

        Company company = companyRepository.findByAppUser_IdentityNumber(managerIdentityNumber)
                .orElseThrow(() -> new BankOperationException("Kurumsal profil bulunamadı!"));

        Account senderAccount = accountRepository.findByIban(request.getSenderIban())
                .orElseThrow(() -> new BankOperationException("Ödeme yapılacak kasa bulunamadı!"));

        // Güvenlik: Kasa bu şirketin mi?
        if (!senderAccount.getAppUser().getIdentityNumber().equals(managerIdentityNumber)) {
            throw new BankOperationException("Güvenlik İhlali: Sadece kendi kurumsal kasalarınızdan ödeme yapabilirsiniz!");
        }

        // Güvenlik: Kasa aktif mi?
        if (!senderAccount.isActive()) {
            throw new BankOperationException("Seçilen kasa pasif durumdadır, işlem yapılamaz!");
        }

        List<CompanyEmployee> employees = companyEmployeeRepository.findByCompanyId(company.getId());
        if (employees.isEmpty()) {
            throw new BankOperationException("Şirketinize kayıtlı personel bulunmamaktadır!");
        }

        // 1. Toplam Maaşı (TRY cinsinden) Hesapla
        BigDecimal totalSalaryInTry = employees.stream()
                .map(CompanyEmployee::getSalaryAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Kasanın Dövizine Göre Çevrim Yap (Bakiye Kontrolü İçin)
        BigDecimal totalRequiredInSenderCurrency = totalSalaryInTry;

        // 🚀 DÜZELTME 1: getCurrency() bir Enum döner. Bunu .name() ile String'e çevirdik!
        if (!senderAccount.getCurrency().name().equalsIgnoreCase("TRY")) {
            Double convertedTotal = currencyService.convertAmount(
                    totalSalaryInTry.doubleValue(),
                    "TRY",
                    senderAccount.getCurrency().name() // 🚀 DÜZELTME
            );
            totalRequiredInSenderCurrency = BigDecimal.valueOf(convertedTotal);
        }

        // 3. Nihai Bakiye Kontrolü
        if (senderAccount.getBalance().compareTo(totalRequiredInSenderCurrency) < 0) {
            throw new BankOperationException("Kasada yeterli bakiye yok! Toplam Gereken: " +
                    String.format("%.2f", totalRequiredInSenderCurrency) + " " + senderAccount.getCurrency().name());
        }

        List<TransactionResponse> transactionResults = new ArrayList<>();

        // 4. TOPLU DAĞITIM DÖNGÜSÜ
        for (CompanyEmployee emp : employees) {
            BigDecimal amountInSenderCurrency = emp.getSalaryAmount();

            // 🚀 DÜZELTME 2: Enum hatası burada da giderildi
            if (!senderAccount.getCurrency().name().equalsIgnoreCase("TRY")) {
                Double converted = currencyService.convertAmount(
                        emp.getSalaryAmount().doubleValue(),
                        "TRY",
                        senderAccount.getCurrency().name() // 🚀 DÜZELTME
                );
                amountInSenderCurrency = BigDecimal.valueOf(converted);
            }

            TransferRequest transferReq = new TransferRequest();
            transferReq.setSenderIban(senderAccount.getIban());
            transferReq.setReceiverIban(emp.getSalaryIban());

            // Motor artık kasanın kendi dövizini görecek!
            transferReq.setAmount(amountInSenderCurrency);
            transferReq.setDescription("Maaş Ödemesi - " + company.getCompanyName());
            transferReq.setSalaryPayment(true);

            // Transferi ateşle ve dekontu listeye ekle
            TransactionResponse response = transactionService.transfer(transferReq);
            transactionResults.add(response);
        }

        return transactionResults;
    }

    private CompanyEmployeeResponse mapToResponse(CompanyEmployee ce) {
        return CompanyEmployeeResponse.builder()
                .id(ce.getId())
                .identityNumber(ce.getRetailCustomer().getAppUser().getIdentityNumber())
                .firstName(ce.getRetailCustomer().getFirstName())
                .lastName(ce.getRetailCustomer().getLastName())
                .salaryIban(ce.getSalaryIban())
                .salaryAmount(ce.getSalaryAmount())
                .build();
    }
}