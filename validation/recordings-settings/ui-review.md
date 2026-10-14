# Review UI rekaman/pengaturan0.3.0

Mode Operate, Material3 native mengikuti PRODUCT/DESIGN, tanpa dependensi atau visual world baru. Impeccable context gagal karena launcher tidak dapat dieksekusi; konteks proyek dan referensi new-work/android/operate/craft-floor dibaca langsung. Ponytail full: gunakan sesi/crypto/decoder/AtomicFile yang ada, date picker dan SAF Android, MediaMuxer/MediaExtractor, satu segmen video-only.

**Status runtime: belum diverifikasi.** Lima upaya emulator37.2.12 pada API37/API30, renderer software/guest dan tanpa KVM berakhir SIGSEGV139 saat boot, sebelum APK terpasang atau tes baru dijalankan. Log dan metadata ada di [results.json](results.json). Tidak ada screenshot fitur baru, putaran konfirmasi visual, hasil TalkBack, atau hasil native MP4 yang diklaim lulus. Tidak memakai browser atau mockup sebagai bukti Android.

Capture historis `.impeccable/review/phone-player-v2.png` dan `phone-dark-large-v2.png` ditinjau bersama sebagai referensi incumbent; warna/Material roles, font sistem, target minimum dan spacing masih sesuai source tema. Capture tersebut **mendahului tab baru**. Tidak ada klaim bahwa capture historis memvalidasi perubahan ini.

Review source mencakup: tab lokal dengan Back yang sama; auth non-live tidak memulai live; Surface16:9 di luar scroll dengan batas setengah viewport; Column/LazyColumn dan insets; target48dp+, minimum52dp untuk aksi utama; row checkbox/switch/radio dengan satu pemilik semantics; label slider posisi; password keyboard/masking/erasure; feedback status live region; loading/cancel/empty/error; draft/readback; konfirmasi meninggalkan pekerjaan; format dengan konsekuensi penghapusan dan Batal. Format dikunci di UI/ViewModel/session selama enum terminal belum terbukti.

`FeatureUiTest` memuat disconnected tabs, fixture settings/storage/recordings berlabel sintetis, dan konfirmasi destructive dengan Batal yang tidak menjalankan format. Test capture dapat dijalankan pada phone light/dark, font scale1.3 dan landscape melalui pengaturan emulator setelah boot tersedia; belum ada bukti visual untuk varian tersebut. Scope test baru terbatas, bukan acceptance kamera.
