# MorphoVision-MVS (Microparticle Visualization System)

MorphoVision-MVS is a specialized research dashboard designed for high-precision morphological analysis of microparticles. It transforms the professional ImageJ engine into a one-click automated pipeline for materials science and pharmaceutical research.

## ⚡ Key Features

- **Automated Research Pipeline**: One-click detection, measurement, and reporting.
- **Unique Morphological Metrics**: Reports 14+ geometric parameters, including unique research metrics:
  - **Heywood Circularity Factor**: Standard for pharmaceutical particle analysis.
  - **PA Fractal Dimension**: Measures boundary roughness of SEM particles.
  - **Solidity & Elongation**: Advanced detection of agglomerates and fibers.
- **Automatic Classifier**: Uses AI-logic to classify particles into *Spherical, Near-Spherical, Elongated, Irregular, or Agglomerate*.
- **Interactive Analytics**: 8 real-time distribution charts with "Expand to Window" capability.
- **Professional Reports**: Generates self-contained HTML reports and CSV data for publications.

## 🚀 How to Run

### Quick Start (Run the App)
If you have the repository cloned, simply run:
```bash
java -jar ij.jar
```

### Build from Source
If you wish to compile the code yourself:
1. **Compile**:
   ```bash
   mkdir build
   javac --release 11 -d build ij/ImageJ.java
   ```
2. **Package & Run**:
   ```bash
   jar cfm ij.jar MANIFEST.MF -C build .
   java -jar ij.jar
   ```

## 🔬 Usage Guide

1. **Launch**: Click the **⚡ Automate Your Work** button in the top toolbar.
2. **Load**: Browse for your SEM/Microscopy image or use the currently open one.
3. **Select Mode**: Choose **SEM**, **Brightfield**, or **Fluorescence** (MorphoVision will auto-tune the filters).
4. **Run**: Click **Run Full Pipeline**.
5. **Analyze**: Explore the **📊 Charts** tab. Double-click any chart to maximize it.
6. **Export**: Save your data as a CSV or generate a full HTML Research Report.

---
*This project is built upon the [ImageJ](https://imagej.nih.gov/ij/) open-source software.*
