# bourse-desktop

نسخه‌ی دسکتاپ/ویندوز موتور بازتعادل پویای پورتفوی PCMR (پورت از اپ اندروید `bourse`).

## وضعیت
- فاز ۱: ماژول `core` (منطق `domain` و مدل‌های داده بدون وابستگی به Android/Room) و تست‌های آن.
- بعدی: لایه‌ی داده (SQLite)، پارس فایل، UI با Compose Multiplatform Desktop، هشدار پس‌زمینه، بسته‌بندی MSI.

## اجرای تست‌ها
    gradle :core:test
