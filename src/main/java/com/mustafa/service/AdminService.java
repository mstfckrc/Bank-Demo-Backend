package com.mustafa.service;

import com.mustafa.dto.request.OpenAccountRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.AccountResponse;
import com.mustafa.dto.response.CustomerResponse;
import com.mustafa.dto.response.TransactionResponse;

import java.util.List;

public interface AdminService {
    List<CustomerResponse> getAllCustomers();
    void deleteCustomer(String tcNo);
    CustomerResponse updateCustomer(String tcNo, UpdateProfileRequest request);
    List<AccountResponse> getCustomerAccounts(String tcNo);

    // YENİ: Sistemdeki tüm hesapları getir
    List<AccountResponse> getAllAccounts();

    List<TransactionResponse> getAccountTransactions(String accountNumber);

    AccountResponse openAccountForCustomer(String tcNo, OpenAccountRequest request);

    // 🚀 YENİ: Adminin müşteri durumunu güncellemesi
    void updateCustomerStatus(String tcNo, String status);
}