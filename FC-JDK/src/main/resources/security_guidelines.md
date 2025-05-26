# 安全须知

1. 请保持设备离线，即不以线缆、移动网络、wifi、蓝牙、NFC等任何方式与其他设备相连。
2. 务必保证私钥、密语绝不泄露或丢失。密语可随时产生私钥，但私钥不能反推密语。
3. 在屏幕显示私钥、密语时，应确保周边安全，没有他人视野、摄像头等。
4. 建议使用无网的扫描二维码设备，物理备份私钥、密语，至少2个备份并分别放置，定期检查。
5. 一旦本设备遗失或可能被刺探破解，请立即使用备份私钥将资产转移到安全的地址上。
6. 采用密语生成私钥时，密语应尽量长、复杂、包含多种类型字符，切勿使用名言诗句等。记住，您面对的是全世界拥有巨大算力和高超技能的黑客。
7. 本设备密语产生私钥的算法为SHA256哈希计算得到，您可以用第三方工具上测试，但请勿使用测试私钥保存资产，切勿用存资产的密语做在线测试。
8. 您可以在安全可信的外部设备中产生私钥，通过扫描或手工输入的方式导入密签。这个环节的安全性将取决于私钥的来源和您的操作。
9. 助记词使私钥安全策略更加复杂，因此，密签不支持助记词，可将第三方设备上导出私钥的导入密签使用。
10. 再次强调：私钥、密语绝不可泄露或丢失，设备必须离线，这是密签存在的意义。
11. 本应用不联网，不收集任何用户和应用数据，因而不对任何数据泄漏负责，用户务必保证设备安全。

# Security Guidelines

1. Keep the device offline at all times, meaning it should not be connected to any other devices via cables, mobile networks, Wi-Fi, Bluetooth, NFC, or any other means.
2. Ensure that private keys and passphrases are never leaked or lost. A passphrase can always generate a private key, but a private key cannot be used to derive a passphrase.
3. When displaying private keys or passphrases on the screen, make sure the surroundings are secure, with no one watching or any cameras recording.
4. It is recommended to use an offline QR code scanning device for physical backup of private keys and passphrases, with at least two separate backups stored in different locations and regularly checked.
5. If this device is lost or suspected to be compromised, immediately transfer assets to a secure address using a backup private key.
6. When generating a private key from a passphrase, the passphrase should be as long and complex as possible, containing multiple types of characters. Do not use famous quotes or poems. Remember, you are up against hackers worldwide with immense computing power and advanced skills.
7. This device generates private keys from passphrases using the SHA-256 hash algorithm. You can test this using third-party tools, but do not store assets with test-generated private keys, and never test an asset-storing passphrase online.
8. You can generate a private key on a secure and trusted external device and import it into the system via scanning or manual entry. The security of this step depends on the source of the private key and your operational precautions.
9. Mnemonics add complexity to private key security strategies; therefore, this system does not support mnemonic phrases. You can import a private key exported from a third-party device instead.
10. Once again: Private keys and passphrases must never be leaked or lost, and the device must remain offline. This is the core purpose of this system.
11. This application does not connect to the internet or collect any user or application data; therefore, it is not responsible for any data leaks. Users must ensure the security of their devices. 