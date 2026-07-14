# ModTheSpire Documentation

## Overview

ModTheSpire is a tool to load external mods for Slay the Spire without modifying the base game files.

**Repository:** https://github.com/kiooeht/ModTheSpire

## Usage

### Installation

1. Download the latest [Release](https://github.com/kiooeht/ModTheSpire/releases).
2. Copy `ModTheSpire.jar` to your Slay the Spire install directory.
   - For Windows, copy `MTS.cmd` to your Slay the Spire install directory.
   - For Linux, copy `MTS.sh` to your Slay the Spire install directory and make it executable.
3. Create a `mods` directory. Place mod JAR files into the `mods` directory.

### Running Mods

1. Run ModTheSpire.
   - For Windows, run `MTS.cmd`.
   - For Linux, run `MTS.sh`.
   - Or run `ModTheSpire.jar` with Java 8.
2. Select the mod(s) you want to use.
3. Press 'Play'.

## For Modders

### Requirements
- JDK 8
- Maven

### General
- ModTheSpire automatically sets the Settings.isModded flag to true.
- [SpirePatch Wiki](https://github.com/kiooeht/ModTheSpire/wiki/SpirePatch)

### Building
1. Run `mvnw package`
