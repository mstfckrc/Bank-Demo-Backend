package com.mustafa.controller.impl;

import com.mustafa.controller.AccountController;
import com.mustafa.dto.request.CreateAccountRequest;
import com.mustafa.dto.response.AccountResponse;
import com.mustafa.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountControllerImpl implements AccountController {

    private final AccountService accountService;

    @Override
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(CreateAccountRequest request) {
        // Hesap başarıyla açıldığında 201 Created dönmek en profesyonelidir
        return new ResponseEntity<>(accountService.createAccount(request), HttpStatus.CREATED);
    }

    @Override
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        // Hesapları başarıyla getirdiğinde 200 OK döner
        return ResponseEntity.ok(accountService.getMyAccounts());
    }

    @Override
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByAccountNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByAccountNumber(accountNumber));
    }

    @Override
    @DeleteMapping("/{accountNumber}")
    public ResponseEntity<String> deleteAccount(@PathVariable String accountNumber) {
        accountService.deleteAccount(accountNumber);
        return ResponseEntity.ok("Hesap başarıyla kapatıldı.");
    }
}