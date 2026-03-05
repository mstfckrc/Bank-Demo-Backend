package com.mustafa.controller.impl;

import com.mustafa.controller.CompanyEmployeeController;
import com.mustafa.dto.request.HireEmployeeRequest;
import com.mustafa.dto.request.UpdateEmployeeRequest;
import com.mustafa.dto.response.CompanyEmployeeResponse;
import com.mustafa.service.CompanyEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies/employees")
@RequiredArgsConstructor
public class CompanyEmployeeControllerImpl implements CompanyEmployeeController {

    private final CompanyEmployeeService companyEmployeeService;

    @Override
    @PostMapping
    public ResponseEntity<CompanyEmployeeResponse> hireEmployee(
            Principal principal,
            @RequestBody HireEmployeeRequest request) { // @Valid arayüzden miras alınır

        String managerIdentityNumber = principal.getName();
        return new ResponseEntity<>(companyEmployeeService.hireEmployee(managerIdentityNumber, request), HttpStatus.CREATED);
    }

    @Override
    @GetMapping
    public ResponseEntity<List<CompanyEmployeeResponse>> getMyEmployees(Principal principal) {

        String managerIdentityNumber = principal.getName();
        return ResponseEntity.ok(companyEmployeeService.getMyEmployees(managerIdentityNumber));
    }

    @Override
    @PutMapping("/{employeeIdentityNumber}")
    public ResponseEntity<CompanyEmployeeResponse> updateEmployee(
            Principal principal,
            @PathVariable String employeeIdentityNumber,
            @RequestBody UpdateEmployeeRequest request) {

        String managerIdentityNumber = principal.getName();
        return ResponseEntity.ok(companyEmployeeService.updateEmployee(managerIdentityNumber, employeeIdentityNumber, request));
    }

    @Override
    @DeleteMapping("/{employeeIdentityNumber}")
    public ResponseEntity<Map<String, String>> removeEmployee(
            Principal principal,
            @PathVariable String employeeIdentityNumber) {

        String managerIdentityNumber = principal.getName();
        companyEmployeeService.removeEmployee(managerIdentityNumber, employeeIdentityNumber);

        // AdminControllerImpl'deki map yapısının aynısı
        Map<String, String> response = new HashMap<>();
        response.put("message", employeeIdentityNumber + " kimlik numaralı personel başarıyla şirketten çıkarıldı.");
        return ResponseEntity.ok(response);
    }
}