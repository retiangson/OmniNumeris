$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing.Common

$outputDirectory = Join-Path $PSScriptRoot '../../docs/benchmarks/math-ocr-corpus'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$font = [Drawing.Font]::new('Cambria Math', 56, [Drawing.GraphicsUnit]::Pixel)
$smallFont = [Drawing.Font]::new('Cambria Math', 34, [Drawing.GraphicsUnit]::Pixel)
$brush = [Drawing.Brushes]::Black
$bar = [Drawing.Pen]::new([Drawing.Color]::Black, 2)
$ink = [Drawing.Pen]::new([Drawing.Color]::FromArgb(28, 30, 38), 4)
$ink.StartCap = [Drawing.Drawing2D.LineCap]::Round
$ink.EndCap = [Drawing.Drawing2D.LineCap]::Round
$ink.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
$culture = [Globalization.CultureInfo]::InvariantCulture

function Write-Fixture([string]$name, [scriptblock]$draw) {
    $bitmap = [Drawing.Bitmap]::new(540, 150)
    try {
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([Drawing.Color]::White)
            $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
            & $draw $graphics
        } finally {
            $graphics.Dispose()
        }
        $bitmap.Save((Join-Path $outputDirectory "$name.png"), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

$strokes = @{
    '1' = '.15,.27 .51,.08 .48,.94|.15,.94 .83,.94'
    '2' = '.07,.29 .16,.12 .47,.07 .75,.16 .85,.36 .58,.59 .12,.91 .88,.90'
    '3' = '.10,.12 .76,.10 .48,.47 .72,.49 .86,.69 .76,.87 .43,.95 .12,.86'
    '4' = '.65,.08 .09,.63 .90,.62|.65,.09 .65,.96'
    '+' = '.44,.25 .44,.78|.09,.52 .80,.51'
    '×' = '.15,.28 .76,.77|.75,.29 .12,.77'
    '/' = '.10,.94 .77,.08'
    'x' = '.10,.34 .73,.87|.73,.36 .14,.87'
    '-' = '.08,.52 .76,.51'
    '=' = '.08,.42 .81,.40|.10,.61 .82,.62'
    '^' = '.16,.42 .43,.12 .72,.43'
}

function Write-SyntheticInk([Drawing.Graphics]$graphics, [string]$expression) {
    $origin = 28.0
    for ($index = 0; $index -lt $expression.Length; $index++) {
        $glyph = $strokes[$expression.Substring($index, 1)]
        $baseline = 32 + (($index * 7) % 9)
        foreach ($segment in $glyph.Split('|')) {
            $points = foreach ($coordinate in $segment.Split(' ')) {
                $pair = $coordinate.Split(',')
                [Drawing.PointF]::new(
                    $origin + [float]::Parse($pair[0], $culture) * 35,
                    $baseline + [float]::Parse($pair[1], $culture) * 66
                )
            }
            $graphics.DrawLines($ink, [Drawing.PointF[]]$points)
        }
        $origin += 41
    }
}

try {
    Write-Fixture 'P01' { param($g) $g.DrawString('2 + 3 × 4', $font, $brush, 28, 42) }
    Write-Fixture 'P02' {
        param($g)
        $g.DrawString('1', $smallFont, $brush, 44, 26)
        $g.DrawString('2', $smallFont, $brush, 44, 80)
        $g.DrawLine($bar, 41, 73, 75, 73)
        $g.DrawString('+', $font, $brush, 93, 43)
        $g.DrawString('1', $smallFont, $brush, 161, 26)
        $g.DrawString('3', $smallFont, $brush, 161, 80)
        $g.DrawLine($bar, 158, 73, 192, 73)
    }
    Write-Fixture 'P03' { param($g) $g.DrawString('√(9/16)', $font, $brush, 28, 42) }
    Write-Fixture 'P04' {
        param($g)
        $g.DrawString('2', $font, $brush, 28, 52)
        $g.DrawString('3', $smallFont, $brush, 64, 31)
    }
    Write-Fixture 'P05' { param($g) $g.DrawString('2×x + 3 = x − 5', $font, $brush, 28, 42) }
    Write-Fixture 'H01' { param($g) Write-SyntheticInk $g '2+3×4' }
    Write-Fixture 'H02' { param($g) Write-SyntheticInk $g '1/2+1/3' }
    Write-Fixture 'H03' { param($g) Write-SyntheticInk $g 'x-1=2' }
    Write-Fixture 'H04' { param($g) Write-SyntheticInk $g '2^3' }
} finally {
    $ink.Dispose()
    $bar.Dispose()
    $smallFont.Dispose()
    $font.Dispose()
}
