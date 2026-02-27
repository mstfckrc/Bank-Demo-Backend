package com.mustafa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 11)
    private String tcNo;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password; // Veritabanında Bcrypt ile şifrelenmiş (hash) hali duracak

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role; // Aşağıda tanımladığımız Enum'ı kullanıyoruz

    // Bir müşterinin birden fazla hesabı olabilir
    // cascade = Bir müşteri silinirse, ona ait tüm hesaplar da silinsin
    // orphanRemoval = Bir hesap müşterinin listesinden çıkarılırsa, veritabanından da silinsin
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Account> accounts;

    // --- SPRING SECURITY USERDETAILS METOTLARI ---

    // 1. Müşterinin yetkilerini (Rollerini) Spring Security'ye veriyoruz
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'PENDING'")
    private ApprovalStatus status = ApprovalStatus.PENDING;

    // 2. Sisteme giriş (Login) yaparken kullanıcı adı olarak neyi baz alacağız?
    // Bankacılık sistemlerinde TC Kimlik No ile giriş çok yaygındır, o yüzden tcNo döndürüyoruz.
    @Override
    public String getUsername() {
        return tcNo;
    }

    // 3. Hesap süresi doldu mu? (Gerçek bir bankada pasif hesap kontrolü yapılabilir, şimdilik hep aktif)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 4. Hesap kilitli mi? (Örn: 3 kez yanlış şifre girince false yapılabilir)
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 5. Şifrenin süresi doldu mu? (Örn: Bankaların 6 ayda bir şifre yeniletmesi)
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 6. Kullanıcı aktif mi? (Örn: Hesabını dondurmuş mu?)
    @Override
    public boolean isEnabled() {
        return true;
    }

    // --- KULLANICI ROLLERİ ---
    public enum Role {
        USER, ADMIN
    }

    // 🚀 2. YENİ EKLENEN ENUM (Onay Durumları)
    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}