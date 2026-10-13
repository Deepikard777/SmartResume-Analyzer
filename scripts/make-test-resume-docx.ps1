# Generates scripts/test-resume.docx — a minimal but valid Word document used by the
# end-to-end check to exercise the DOCX upload path.
#
# A .docx is an OPC zip. It is built here with System.IO.Compression directly (rather
# than Compress-Archive, which writes backslash entry names that strict OOXML readers
# reject). No external tooling or network access is required.
#
# Usage: powershell -ExecutionPolicy Bypass -File scripts/make-test-resume-docx.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$outPath = Join-Path $PSScriptRoot 'test-resume.docx'
if (Test-Path $outPath) { Remove-Item -Force $outPath }

$lines = @(
  'Asha Sharma',
  'asha.sharma@example.com | +91 90000 00000 | github.com/ashasharma',
  '',
  'Professional Summary',
  'Backend-focused software engineer building REST APIs and relational data services for student projects.',
  '',
  'Technical Skills',
  'Java, Spring Boot, Spring MVC, REST APIs, SQL, PostgreSQL, Hibernate, Maven, Git, JUnit, React, TypeScript',
  '',
  'Work Experience',
  'Software Engineer Intern, Acme Systems, Jun 2024 - Dec 2024',
  'Built REST APIs in Java and Spring Boot backed by PostgreSQL for an internal reporting tool.',
  'Wrote JUnit tests and reviewed SQL query plans to reduce report generation time.',
  '',
  'Projects',
  'SmartResume Analyzer - Java, Spring Boot, React, TypeScript, PostgreSQL, Docker',
  'Implemented resume parsing for PDF and DOCX files and a deterministic ATS scoring engine.',
  '',
  'Education',
  'B.Tech Computer Science, 2025',
  '',
  'Certifications',
  'Oracle Certified Associate, Java SE 8 Programmer'
)

function Escape-Xml([string]$value) {
  return $value.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;').Replace('"', '&quot;')
}

$paragraphs = foreach ($line in $lines) {
  if ([string]::IsNullOrEmpty($line)) {
    '<w:p/>'
  } else {
    '<w:p><w:r><w:t xml:space="preserve">' + (Escape-Xml $line) + '</w:t></w:r></w:p>'
  }
}

$contentTypes = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
'@

$rels = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
'@

$document = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + "`n" +
  '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' +
  ($paragraphs -join '') +
  '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr></w:body></w:document>'

$utf8 = New-Object System.Text.UTF8Encoding($false)

$stream = [System.IO.File]::Open($outPath, [System.IO.FileMode]::CreateNew)
try {
  $archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create, $true)
  try {
    $entries = [ordered]@{
      '[Content_Types].xml' = $contentTypes
      '_rels/.rels'         = $rels
      'word/document.xml'   = $document
    }
    foreach ($name in $entries.Keys) {
      $entry = $archive.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
      $entryStream = $entry.Open()
      try {
        $bytes = $utf8.GetBytes($entries[$name])
        $entryStream.Write($bytes, 0, $bytes.Length)
      } finally {
        $entryStream.Dispose()
      }
    }
  } finally {
    $archive.Dispose()
  }
} finally {
  $stream.Dispose()
}

$info = Get-Item $outPath
Write-Output "Wrote $($info.FullName) ($($info.Length) bytes)"

# Read it back to prove the zip is well formed.
$readStream = [System.IO.File]::OpenRead($outPath)
try {
  $check = New-Object System.IO.Compression.ZipArchive($readStream, [System.IO.Compression.ZipArchiveMode]::Read, $false)
  try {
    Write-Output ("entries: " + (($check.Entries | ForEach-Object { $_.FullName }) -join ', '))
  } finally {
    $check.Dispose()
  }
} finally {
  $readStream.Dispose()
}
