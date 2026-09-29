# bourse-desktop

نسخه‌ی دسکتاپ/ویندوز موتور بازتعادل پویای پورتفوی PCMR (پورت از اپ اندروید `bourse`).

## وضعیت
- ماژول `core`: منطق `domain` و مدل‌ها (بدون Android)، با تست.
- ماژول `app`: Compose Desktop، لایه‌ی SQLite/JDBC، پارس اکسل، هشدار در System Tray، چند پورتفوی (انتخاب‌گر در نوار بالا؛ هر پورتفو اکسل، دارایی‌ها، تاریخچه و سیگنال جدا دارد؛ تنظیمات استراتژی و دسته‌بندی صندوق‌ها مشترک است).

## اجرا و تست
    gradle :core:test :app:test
    gradle :app:run

## ساخت MSI ویندوز
ساخت MSI فقط روی ویندوز (با JDK 21 و WiX 3) ممکن است:

    gradle :app:packageMsi        # خروجی: app/build/compose/binaries/main/msi/

یا در گیت‌هاب: Actions ← «Windows MSI» ← Run workflow (فایل MSI به‌عنوان artifact ذخیره می‌شود).

کلید FundBase از `.env` (اولویت) یا `.env.example` خوانده می‌شود.
