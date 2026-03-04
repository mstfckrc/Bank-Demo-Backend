package com.mustafa.controller.impl;

import com.mustafa.controller.AdminController;
import com.mustafa.dto.request.OpenAccountRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.AccountResponse;
import com.mustafa.dto.response.CustomerResponse;
import com.mustafa.dto.response.TransactionResponse;
import com.mustafa.dto.response.UserProfileResponse;
import com.mustafa.service.AdminService;
import com.mustafa.service.TransactionService; // 🚀 YENİ EKLENDİ
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminControllerImpl implements AdminController {

    private final AdminService adminService;
    private final TransactionService transactionService; // 🚀 YENİ EKLENDİ: Uzmana direkt emir vereceğiz

    @Override
    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(adminService.getAllAccounts());
    }

    @Override
    @GetMapping("/customers/{tcNo}/accounts")
    public ResponseEntity<List<AccountResponse>> getCustomerAccounts(@PathVariable String tcNo) {
        return ResponseEntity.ok(adminService.getCustomerAccounts(tcNo));
    }

    @Override
    @GetMapping("/customers")
    public ResponseEntity<List<UserProfileResponse>> getAllCustomers() {
        return ResponseEntity.ok(adminService.getAllCustomers());
    }

    @Override
    @DeleteMapping("/customers/{tcNo}")
    public ResponseEntity<Map<String, String>> deleteCustomer(@PathVariable String tcNo) {
        adminService.deleteCustomer(tcNo);
        Map<String, String> response = new HashMap<>();
        response.put("message", tcNo + " kimlik numaralı müşteri ve bağlı tüm hesapları başarıyla silinmiştir.");
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/customers/{tcNo}")
    public ResponseEntity<UserProfileResponse> updateCustomer(@PathVariable String tcNo, @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(adminService.updateCustomer(tcNo, request));
    }

    @Override
    @GetMapping("/accounts/{accountNumber}/transactions")
    public ResponseEntity<List<TransactionResponse>> getAccountTransactions(@PathVariable String accountNumber) {
        return ResponseEntity.ok(adminService.getAccountTransactions(accountNumber));
    }

    @Override
    @PostMapping("/customers/{tcNo}/accounts")
    public ResponseEntity<AccountResponse> openAccountForCustomer(
            @PathVariable String tcNo,
            @RequestBody OpenAccountRequest request) {
        return ResponseEntity.ok(adminService.openAccountForCustomer(tcNo, request));
    }

    @PutMapping("/customers/{tcNo}/status")
    @Override
    public ResponseEntity<Map<String, String>> updateCustomerStatus(
            @PathVariable String tcNo,
            @RequestParam String status) {

        adminService.updateCustomerStatus(tcNo, status);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Müşteri durumu başarıyla güncellendi: " + status);
        return ResponseEntity.ok(response);
    }

    // --- 🚀 YENİ: MERKEZİ İŞLEM İZLEME VE ONAY (GOD MODE) ---

    @Override
    @GetMapping("/transactions") // GET /api/v1/admin/transactions
    public ResponseEntity<List<TransactionResponse>> getAllTransactions(
            @RequestParam(required = false) String status) {
        // İşi uzmanına (TransactionService) devrediyoruz
        return ResponseEntity.ok(transactionService.getAllTransactionsForAdmin(status));
    }

    @Override
    @PutMapping("/transactions/{referenceNo}/approve") // PUT /api/v1/admin/transactions/{ref}/approve
    public ResponseEntity<TransactionResponse> approveTransaction(@PathVariable String referenceNo) {
        return ResponseEntity.ok(transactionService.approveTransaction(referenceNo));
    }

    @Override
    @PutMapping("/transactions/{referenceNo}/reject") // PUT /api/v1/admin/transactions/{ref}/reject
    public ResponseEntity<TransactionResponse> rejectTransaction(@PathVariable String referenceNo) {
        return ResponseEntity.ok(transactionService.rejectTransaction(referenceNo));
    }
}