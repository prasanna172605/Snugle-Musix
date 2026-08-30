<div align="center">

<img src="assets/Snuggle-new.png" alt="Snuggle Musix Logo" width="100" />

# Snuggle Musix

### A free, open-source music player for Android.

A modern music listening experience built around Material 3, personalisation, and powerful playback features.

[![Website](https://img.shields.io/badge/Website-prasanna0705.netlify.app-FF3366?style=for-the-badge&logo=googlechrome&logoColor=white)](https://prasanna0705.netlify.app/Snuggle-Musix)
[![Latest Release](https://img.shields.io/github/v/release/prasanna172605/Snugle-Musix?style=for-the-badge&color=8c52ff&logo=github)](https://github.com/prasanna172605/Snugle-Musix/releases/latest)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue?style=for-the-badge)](LICENSE)

</div>

---

## Overview

**Snuggle Musix** is a free, open-source music player for Android designed around a modern Material 3 experience. It combines clean visual design with rich audio playback capabilities, giving you full control over your listening experience with zero advertisements and zero invasive telemetry.

Whether streaming your favorite tracks or playing local audio files, Snuggle Musix delivers high fidelity sound, real-time synchronized lyrics, and personalized themes that adapt to your device.

---

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Home</b></td>
      <td align="center"><b>Now Playing</b></td>
      <td align="center"><b>Synced Lyrics</b></td>
    </tr>
    <tr>
      <td><img src="Screenshots/Homepage.jpeg" width="230" alt="Snuggle Musix Home" /></td>
      <td><img src="Screenshots/Music Player.jpeg" width="230" alt="Snuggle Musix Player" /></td>
      <td><img src="Screenshots/Synchronized lyrics.jpeg" width="230" alt="Snuggle Musix Lyrics" /></td>
    </tr>
    <tr>
      <td align="center"><b>Search & Explore</b></td>
      <td align="center"><b>Library & Playlists</b></td>
      <td align="center"><b>Song Recognition</b></td>
    </tr>
    <tr>
      <td><img src="Screenshots/Search and Explore.jpeg" width="230" alt="Snuggle Musix Search" /></td>
      <td><img src="Screenshots/Playlist & library.jpeg" width="230" alt="Snuggle Musix Library" /></td>
      <td><img src="Screenshots/Song recognition.jpeg" width="230" alt="Snuggle Musix Song Recognition" /></td>
    </tr>
  </table>
</div>

---

## Features

- **Material 3 & Liquid Glass UI**: Clean interface with dynamic color theming that adapts to your album artwork and system palette.
- **Synchronized Lyrics**: Word-by-word, real-time synchronized lyrics with translation and romanization options.
- **Audio Quality & Equalizer**: Pristine audio output with an integrated 10-band graphic equalizer, bass boost, and loudness enhancer.
- **Offline Playback & Downloads**: Save tracks, albums, and playlists locally for high quality offline listening.
- **Song Recognition**: Identify songs playing in your environment and jump straight to streaming.
- **AI-Powered Voice Search**: Natural voice recognition to find songs, artists, and playlists hands-free.
- **Listen Together**: Synchronize real-time playback across multiple devices with friends.
- **Home Screen Widgets**: Multiple interactive Android widgets for quick playback control.
- **100% Ad-Free & Privacy First**: No advertisements, no paywalls, and no tracking.

---

## Why Snuggle Musix?

Snuggle Musix is built around a simple idea: music players should feel personal.

Many modern music apps have become cluttered with unwanted social feeds, intrusive advertisements, and rigid interfaces. Snuggle Musix focuses on thoughtful interaction design, fluid performance, audio customization, and giving users true ownership of their music listening experience.

---

## Tech Stack

Snuggle Musix is built natively for Android using modern Android architecture components:

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 Design
- **Media Playback Engine**: [AndroidX Media3](https://developer.android.com/guide/topics/media/media3) & ExoPlayer
- **Database**: [Room](https://developer.android.com/training/data-storage/room) & SQLite
- **Networking**: [Ktor](https://ktor.io/) & [OkHttp](https://square.github.io/okhttp/)
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Audio Equalization**: Android Native AudioFx APIs

---

## Download

The latest pre-built APK is available directly on GitHub Releases:

📦 **[Download Snuggle Musix APK](https://github.com/prasanna172605/Snugle-Musix/releases/latest)**

You can also visit the official website: **[https://prasanna0705.netlify.app/Snuggle-Musix](https://prasanna0705.netlify.app/Snuggle-Musix)**

---

## Build from Source

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17 or JDK 21
- Android SDK 35

### Clone & Compile

```bash
# 1. Clone the repository
git clone https://github.com/prasanna172605/Snugle-Musix.git
cd Snugle-Musix

# 2. Build the debug APK
./gradlew assembleDebug

# 3. Output location
# The generated APK will be located at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## Contributing

Contributions, bug reports, and feature requests are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

Snuggle Musix is licensed under the [GNU General Public License v3.0 (GPLv3)](LICENSE).
