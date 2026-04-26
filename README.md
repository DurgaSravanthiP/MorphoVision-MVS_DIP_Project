# MorphoVision-MVS (Microparticle Visualization System)

[![Build Status](https://github.com/shruthideepikareddy/MorphoVision-MVS/actions/workflows/build.yml/badge.svg)](https://github.com/shruthideepikareddy/MorphoVision-MVS/actions/workflows/build.yml)

MorphoVision-MVS is a specialized research dashboard built on the ImageJ framework, designed for high-precision morphological analysis of microparticles. It provides an end-to-end workflow from image acquisition to interactive data visualization.

## Key Features

- **Unified Research Dashboard**: A streamlined UI (MorphoWizard) for managing the entire analysis pipeline.
- **Advanced Particle Analysis**: Automated detection and measurement of geometric properties including Area, Perimeter, Circularity, Curvature, and Fractal dimensions.
- **Interactive Visualization**: Real-time plotting and distribution analysis of morphological metrics using the integrated MorphoPlotter.
- **Premium Design**: Modern, theme-aware interface (MorphoTheme) optimized for research environments.

## Getting Started

### Prerequisites

It is recommended to build MorphoVision-MVS with OpenJDK 8 or 11.

### Building from Source

#### With Maven
```bash
mvn clean install
mvn -Pexec
```

#### With Ant
The [Apache Ant](https://ant.apache.org/) utility can also be used to compile and run the project using the `build.xml` file.

## Usage

1. **Launch the Wizard**: Start ImageJ and navigate to the MorphoVision Research Wizard.
2. **Load Image**: Open your microparticle micrographs.
3. **Pre-process**: Adjust thresholds and filters to isolate particles.
4. **Analyze**: Run the MorphoAnalysis to calculate geometric metrics.
5. **Visualize**: Explore the results via interactive distribution plots and results tables.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---
*This project is built upon the [ImageJ](https://imagej.nih.gov/ij/) open-source software.*
