# HexPrint

Android PDF printing for dimensioned paper templates.

## Features
- Open a PDF using Android's file picker, with a preview and measured source page size.
- Render at **100% of original PDF dimensions**, centered on US Letter or A4, or enter a custom document scale.
- Calibrate the printer **independently in X and Y**, then verify with a 100 mm horizontal reference, 100 mm vertical reference, and 110 × 70 mm case outline.
- Persist the calibration factors and paper selection.
- Build and emulator-install test using GitHub Actions.

## Printing accuracy
The app generates a PDF at explicitly defined dimensions: 72 PDF points per inch. Android's downstream print service and printer firmware may still shrink, expand, or reposition the output. There is no physical size guarantee from software alone.

**Workflow:** select Letter paper and bordered (not borderless) printing; print a RAW calibration sheet; physically measure both 100 mm bars with calipers; enter the two measured lengths, tap **Save raw calibration**; print a CORRECTED verification sheet; measure again. If needed, enter verified lengths and choose **Refine using verification**. Don't change paper/driver settings after calibrating.

Avoid edge clipping and do not enable "Fit to page" in a print service if it offers that setting. Calibration only holds for the same printer, media, orientation, and print-service configuration.

Note: imported PDFs are rasterized at up to 300 DPI before insertion into the output PDF to avoid needing third-party libraries. Very detailed vector artwork may lose quality.

## Development
The GitHub Actions workflow uses Gradle 8.9, JDK 17, Android SDK 35. It runs unit tests, creates an APK, installs and opens it in an Android emulator, then uploads an APK artifact.

Package: `dev.johnds.hexprint`
