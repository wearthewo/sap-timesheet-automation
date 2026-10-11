import { inflateRawSync } from "node:zlib";
// Read the ZIP central directory so this also handles POI's streaming data descriptors.
export function workbookXml(bytes: Buffer): Map<string, string> {
 const files = new Map<string, string>();
 let end = bytes.length - 22;
 while (end >= 0 && bytes.readUInt32LE(end) !== 0x06054b50) end--;
 if (end < 0) throw new Error("Invalid XLSX ZIP directory");
 let offset = bytes.readUInt32LE(end + 16);
 const count = bytes.readUInt16LE(end + 10);
 for (let i = 0; i < count; i++) {
  if (bytes.readUInt32LE(offset) !== 0x02014b50) throw new Error("Invalid ZIP entry");
  const method = bytes.readUInt16LE(offset + 10), size = bytes.readUInt32LE(offset + 20);
  const nameLength = bytes.readUInt16LE(offset + 28), extra = bytes.readUInt16LE(offset + 30), comment = bytes.readUInt16LE(offset + 32);
  const local = bytes.readUInt32LE(offset + 42), name = bytes.toString("utf8", offset + 46, offset + 46 + nameLength);
  const start = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28);
  const data = bytes.subarray(start, start + size);
  if (name.endsWith(".xml")) files.set(name, (method === 8 ? inflateRawSync(data) : data).toString("utf8"));
  offset += 46 + nameLength + extra + comment;
 }
 return files;
}
