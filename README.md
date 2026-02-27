# 🏦 Sec-Demo: Bank Management System API

**Sec-Demo**, kurumsal mimari standartlarında geliştirilmiş, güvenli ve ölçeklenebilir bir bankacılık backend projesidir. **Spring Boot 3** ve **Spring Security (JWT)** tabanlı bu sistem, hem bireysel bankacılık işlemlerini hem de kapsamlı yönetici (Admin) yeteneklerini sunar.

---

## 🚀 Öne Çıkan Özellikler

- **Güçlü Yetkilendirme (RBAC):** JWT tabanlı USER ve ADMIN rolleriyle tam kontrol.
- **Hassas Para Yönetimi:** BigDecimal kullanımı ve Merkez Bankası kur entegrasyonu ile kayıpsız döviz çevrimi (TRY - USD).
- **Global Exception Handling:** Tüm hataların şık ve standart JSON formatında dönmesi.
- **Cascade Temizliği:** Bir müşteri silindiğinde bağlı tüm hesapların otomatik temizlenmesi (orphanRemoval).
- **Profil Yönetimi:** Kullanıcıların kendi bilgilerini ve şifrelerini güvenli güncelleme imkanı.

---

## 🛠️ Teknoloji Yığını

- **Java 21 / Spring Boot 3**
- **Spring Security & JWT** (Authentication & Authorization)
- **PostgreSQL** (Database)
- **Spring Data JPA & Hibernate**
- **Lombok** (Boilerplate code reduction)
- **Swagger / OpenAPI 3** (API Documentation)

---

## 📡 API Uç Noktaları (Endpoints)

### 🔐 Kimlik Doğrulama (Auth)
- `POST /api/v1/auth/register` - Yeni müşteri kaydı.
- `POST /api/v1/auth/login` - Giriş ve JWT Token alımı.

### 👤 Müşteri İşlemleri (Customer)
- `PUT /api/v1/customers/profile` - Profil bilgisi güncelleme.
- `PUT /api/v1/customers/password` - Güvenli şifre değiştirme.

### 🛡️ Yönetici Paneli (Admin)
- `GET /api/v1/admin/customers` - Tüm müşteri listesi ve rolleri.
- `GET /api/v1/admin/accounts` - Bankadaki tüm hesapların dökümü.
- `GET /api/v1/admin/customers/{tcNo}/accounts` - Müşteriye özel hesap listesi.
- `PUT /api/v1/admin/customers/{tcNo}` - Müşteri verisi güncelleme.
- `DELETE /api/v1/admin/customers/{tcNo}` - Müşteriyi ve hesaplarını silme.

---

## ⚠️ Hata Yönetimi
İş kuralları hataları BankOperationException üzerinden yönetilir. Örnek hata yanıtı:

```json
{
  "timestamp": "2026-02-20T11:45:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Müşteri bulunamadı!",
  "path": "/api/v1/admin/customers/12345678901/accounts"
}
```

---

## 🛠️ Kurulum

1. PostgreSQL bağlantı bilgilerini application.properties içine girin.
2. Projeyi derleyin: `mvn clean install`
3. Çalıştırın ve Swagger ile test edin: `http://localhost:8080/swagger-ui.html`

---
*Geliştirici: Mustafa*