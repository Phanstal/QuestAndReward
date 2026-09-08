# App Store icons must be opaque; preserve the artwork on the app's dark background.
Add-Type -AssemblyName System.Drawing
$questIconPath = Join-Path $PSScriptRoot '../iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon.png'
$questSource = [System.Drawing.Bitmap]::new($questIconPath)
$questOutput = [System.Drawing.Bitmap]::new(1024, 1024, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$questGraphics = [System.Drawing.Graphics]::FromImage($questOutput)
try {
    $questGraphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#16153A'))
    $questGraphics.DrawImage($questSource, 0, 0, 1024, 1024)
    $questSource.Dispose()
    $questOutput.Save($questIconPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $questGraphics.Dispose()
    $questOutput.Dispose()
    $questSource.Dispose()
}
