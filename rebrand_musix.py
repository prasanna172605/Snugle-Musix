import os

res_dir = r"c:\Users\PRASANNA\Downloads\Echo-Music-5.1.92\app\src\main\res"

replacements = {
    "Snuggle Music": "Snugle Musix",
    "SnuggleMusic": "SnugleMusix",
    "snugglemusic": "snuglemusix"
}

for root, dirs, files in os.walk(res_dir):
    for filename in files:
        if filename.endswith(".xml"):
            file_path = os.path.join(root, filename)
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                modified = False
                for target, replacement in replacements.items():
                    if target in content:
                        content = content.replace(target, replacement)
                        modified = True
                
                if modified:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(content)
                    print(f"Rebranded strings in: {os.path.relpath(file_path, res_dir)}")
            except Exception as e:
                print(f"Error reading {file_path}: {e}")
