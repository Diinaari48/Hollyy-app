import json

with open("full_prompt_from_truncation.txt", "r") as f:
    text = f.read()
print(f"Total length: {len(text)}")
