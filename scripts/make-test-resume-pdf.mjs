/**
 * Generates a small, valid, text-based PDF resume used as the end-to-end test
 * fixture (scripts/test-resume.pdf).
 *
 * Written by hand rather than with a library so no extra dependency is needed:
 * a single Helvetica page with real text operators, so PDFBox can extract text
 * from it. The content deliberately contains the section headings and skills the
 * structure and skill analyzers look for.
 *
 * Usage:  node scripts/make-test-resume-pdf.mjs
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const target = resolve(here, 'test-resume.pdf');

const LINES = [
  'Alex Morgan',
  'alex.morgan@example.com | +1 555 0142 | linkedin.com/in/alexmorgan',
  '',
  'Summary',
  'Software engineer with four years of experience building REST APIs and',
  'data-driven web applications for payments and logistics teams.',
  '',
  'Skills',
  'Java, Spring Boot, REST APIs, SQL, PostgreSQL, MySQL, React, TypeScript,',
  'JavaScript, HTML, CSS, Git, GitHub, JUnit, Mockito, Maven, Linux',
  '',
  'Experience',
  'Software Engineer, Meridian Payments, 2022 - Present',
  'Built REST APIs in Java and Spring Boot backed by PostgreSQL, serving',
  '12k requests per minute. Wrote JUnit and Mockito test suites for the ledger',
  'service. Worked with React and TypeScript on the internal operations console.',
  '',
  'Software Engineer, Northwind Logistics, 2020 - 2022',
  'Developed backend services in Java with MySQL, and automated the release',
  'pipeline with Maven and GitHub Actions.',
  '',
  'Projects',
  'Resume Analyzer - Java, Spring Boot, React, PostgreSQL',
  'Task Manager - TypeScript, React, SQL',
  '',
  'Education',
  'B.Tech Computer Science, 2020',
  '',
  'Certifications',
  'Oracle Certified Associate, Java SE 8',
];

function escapePdfText(value) {
  return value.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

const contentLines = ['BT', '/F1 11 Tf', '14 TL', '60 760 Td'];
for (const line of LINES) {
  if (line === '') {
    contentLines.push('T*');
  } else {
    contentLines.push(`(${escapePdfText(line)}) Tj`, 'T*');
  }
}
contentLines.push('ET');
const content = contentLines.join('\n');

const objects = [
  '<< /Type /Catalog /Pages 2 0 R >>',
  '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
  '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
  `<< /Length ${Buffer.byteLength(content, 'latin1')} >>\nstream\n${content}\nendstream`,
  '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
];

let pdf = '%PDF-1.4\n';
const offsets = [];
objects.forEach((body, index) => {
  offsets.push(Buffer.byteLength(pdf, 'latin1'));
  pdf += `${index + 1} 0 obj\n${body}\nendobj\n`;
});

const xrefOffset = Buffer.byteLength(pdf, 'latin1');
pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
for (const offset of offsets) {
  pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
}
pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;

mkdirSync(here, { recursive: true });
writeFileSync(target, Buffer.from(pdf, 'latin1'));
console.log(`Wrote ${target} (${Buffer.byteLength(pdf, 'latin1')} bytes, ${LINES.length} lines)`);
