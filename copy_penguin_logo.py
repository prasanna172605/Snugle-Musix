import os
import shutil

src = r"C:\Users\PRASANNA\.gemini\antigravity-ide\brain\09135ca4-4aec-4de2-bc54-4fc1eb0abc15\media__1781960419143.png"
res_dir = r"c:\Users\PRASANNA\Downloads\Echo-Music-5.1.92\app\src\main\res"

if not os.path.exists(src):
    print(f"Error: Source image not found at {src}")
    exit(1)

# Destination 1: main drawable icon.png
shutil.copy2(src, os.path.join(res_dir, "drawable", "icon.png"))
shutil.copy2(src, os.path.join(res_dir, "drawable", "ic_launcher_nobg.png"))

for folder in os.listdir(res_dir):
    folder_path = os.path.join(res_dir, folder)
    if os.path.isdir(folder_path) and (folder.startswith("mipmap-") or folder.startswith("drawable-")):
        for filename in os.listdir(folder_path):
            if filename.startswith("ic_launcher") and filename.endswith(".png"):
                dest = os.path.join(folder_path, filename)
                print(f"Replacing launcher icon: {os.path.relpath(dest, res_dir)}")
                shutil.copy2(src, dest)
            elif filename == "legacy_icon.png" or filename == "legacy_icon_round.png":
                dest = os.path.join(folder_path, filename)
                print(f"Replacing legacy launcher icon: {os.path.relpath(dest, res_dir)}")
                shutil.copy2(src, dest)
            elif filename == "icon.png" or filename == "echomusicnotification.png" or filename == "snugglemusicnotification.png":
                dest = os.path.join(folder_path, filename)
                print(f"Replacing icon: {os.path.relpath(dest, res_dir)}")
                shutil.copy2(src, dest)
