// createHash.js
import bcrypt from "bcryptjs";

async function main() {
  const plain = process.argv[2];
  if (!plain) {
    console.error("Usage: node createHash.js <password>");
    process.exit(1);
  }
  const saltRounds = 10;
  const hash = await bcrypt.hash(plain, saltRounds);
  console.log(hash);
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
