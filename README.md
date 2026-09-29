# Better Third Person

> **Status:** ✅ Berhasil di-build dan berjalan di Minecraft 1.21.5 (Fabric).
> Jar hasil build: `build/libs/betterthirdperson-1.0.0.jar`

Fabric mod untuk Minecraft **1.21.5**.

Free-look camera ala **Leawind's Third Person**, khusus untuk third person back
view (F5, bukan F5 dua kali yang menghadap depan). Kamera dilepas dari rotasi
badan player: kamu bisa menggerakkan mouse untuk melihat sekeliling tanpa
memutar karakter, dan badan baru menyusul menghadap arah kamera saat kamu
bergerak, menyerang, atau memakai item.

## Fitur utama

- **Kamera lebih ke belakang, lebih tinggi, dan digeser ke kanan** — badan
  player tampil di sisi kiri layar, bukan menutupi tengah layar seperti third
  person vanilla.
- **Free-look sungguhan**: gerakan mouse hanya memutar kamera (disimpan di
  `CameraState`), bukan badan karakter — mirip mode free-look di game third
  person modern.
- **Badan otomatis mengikuti kamera** saat kamu bergerak atau menyerang/
  memakai item/menambang, walau kamera sedang melihat wajah karakter sendiri.
- **Moonwalk saat mundur**: tahan **S** saja (tanpa W), badan menghadap
  kamera dan berjalan "maju" secara visual. Kalau sambil menahan S kamu
  klik (menyerang/menambang), badan berbalik menghadap crosshair dan benar-
  benar mundur (moonwalk) selama masih ada aksi, lalu setelah 2 detik tanpa
  aksi badan berbalik lagi.
- **Crosshair kustom**: vanilla tidak menggambar crosshair di third person,
  jadi mod ini menambahkannya sendiri saat kamera bebas aktif.
- **Raycast target diperbaiki**: crosshair/serangan mengikuti arah kamera
  bebas (bukan mata karakter), dan otomatis mengabaikan objek di antara
  kamera dan karakter saat kamera melihat punggung sendiri — kecuali kamu
  baru saja sengaja berbalik untuk menyerang ke belakang.
- **Menu pengaturan in-game**: slider untuk jarak, ketinggian, geser
  samping, dan tambahan jarak serang, tersimpan ke
  `config/betterthirdperson.properties`. Keybind untuk membuka menu ini
  bisa diatur lewat Options > Controls (default: belum di-bind).

Tampilan first person dan third person front-facing (F5 dua kali) sama
sekali tidak disentuh mod ini.

## Cara kerja

- `CameraMixin` mengganti perhitungan posisi kamera vanilla di third person
  back view: rotasi diambil dari `CameraState` (bukan yaw/pitch player), dan
  posisi digeser mundur/naik/kanan sesuai `Config`.
- `MouseMixin` mencegat rotasi mouse vanilla dan mengarahkannya ke
  `CameraState` alih-alih langsung ke player, saat kamera bebas aktif.
- `BetterThirdPersonClient` menjalankan logika tick: menentukan kapan badan
  menyusul arah kamera (bergerak/menyerang), serta logika moonwalk saat
  mundur.
- `GameRendererMixin` mengganti ulang raycast target (`findCrosshairTarget`)
  supaya dimulai dari posisi kamera visual & arah free-look, bukan dari mata
  player seperti vanilla — ini memperbaiki target yang meleset karena kamera
  sudah tidak sejajar dengan mata player lagi.
- `LivingEntityMovementMixin` memutar arah gerakan world-space supaya
  perjalanan tetap konsisten "menuju kamera" baik saat badan menghadap
  kamera maupun saat backpedal.
- `CrosshairOverlay` menggambar crosshair sederhana saat kamera bebas aktif.

## Pengaturan (bisa diubah lewat menu in-game)

| Slider | Default | Rentang |
|---|---|---|
| Jarak kamera | 5.0 | 1.0 – 10.0 |
| Ketinggian kamera | 0.55 | -2.0 – 3.0 |
| Geser ke samping | 0.6 | -3.0 – 3.0 |
| Tambahan jarak pukul | 0.0 | 0.0 – 6.0 |

Catatan: "Tambahan jarak pukul" hanya memperluas raycast target di client
(apa yang bisa kamu klik/serang), bukan atribut reach sebenarnya — server
dengan anti-cheat/reach check sendiri tetap bisa menolak interaksi di luar
jarak vanilla.

## Build
```
./gradlew build      # -> build/libs/betterthirdperson-1.0.0.jar
./gradlew runClient
```

## Client atau server?
**Client-side only.** Tidak ada yang perlu dipasang di server; berjalan
normal di singleplayer maupun multiplayer.

## Credits
Made by **Geord**. GitHub: https://github.com/clainenxx
Licensed under MIT (see `LICENSE`).
