import json
import shutil
import zipfile
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

# Default location: LegendaryItemsPack folder right next to this script
DEFAULT_PACK_DIR = Path(__file__).resolve().parent / "LegendaryItemsPack"


def find_assets_root(file_path: Path) -> Path | None:
    """Walk up from a selected .json or .png file to find the enclosing 'assets' directory."""
    for parent in file_path.parents:
        if parent.name == "assets":
            return parent
    return None


def get_existing_item_ids(dest_pack: Path) -> list[str]:
    """Scan assets/legendary/items/*.json and return sorted weapon IDs."""
    items_dir = dest_pack / "assets" / "legendary" / "items"
    ids = set()
    if items_dir.exists():
        for jf in items_dir.glob("*.json"):
            ids.add(jf.stem.lower())
    if not ids:
        ids.update(["astral_sovereign", "mjolnir", "storm_shuriken"])
    return sorted(ids)


class WeaponImporterApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("Legendary Items — Resource Pack Importer")
        self.root.geometry("580x460")
        self.root.resizable(False, False)
        self.root.configure(bg="#18181b")

        # Center window on screen
        self.root.update_idletasks()
        x = (self.root.winfo_screenwidth() // 2) - 290
        y = (self.root.winfo_screenheight() // 2) - 230
        self.root.geometry(f"580x460+{x}+{y}")

        self.dest_pack = DEFAULT_PACK_DIR
        self.src_model: Path | None = None
        self.src_png: Path | None = None
        self.assets_root: Path | None = None

        self._setup_styles()
        self._build_ui()

    def _setup_styles(self):
        style = ttk.Style()
        style.theme_use("clam")

        bg_dark = "#18181b"
        bg_card = "#27272a"
        fg_text = "#f4f4f5"
        fg_muted = "#a1a1aa"
        accent = "#10b981"
        accent_hover = "#059669"

        style.configure("TFrame", background=bg_dark)
        style.configure("Card.TFrame", background=bg_card)
        style.configure("TLabel", background=bg_dark, foreground=fg_text, font=("Segoe UI", 10))
        style.configure("Header.TLabel", background=bg_dark, foreground="#ffffff", font=("Segoe UI", 14, "bold"))
        style.configure("Sub.TLabel", background=bg_dark, foreground=fg_muted, font=("Segoe UI", 9))
        style.configure("CardTitle.TLabel", background=bg_card, foreground="#ffffff", font=("Segoe UI", 10, "bold"))
        style.configure("CardText.TLabel", background=bg_card, foreground="#d4d4d8", font=("Segoe UI", 9))
        style.configure("Match.TLabel", background=bg_card, foreground="#34d399", font=("Segoe UI", 9, "bold"))

        style.configure(
            "Secondary.TButton",
            background="#3f3f46",
            foreground="#ffffff",
            font=("Segoe UI", 9, "bold"),
            borderwidth=0,
            padding=(12, 6),
        )
        style.map("Secondary.TButton", background=[("active", "#52525b")])

        style.configure(
            "Primary.TButton",
            background=accent,
            foreground="#09090b",
            font=("Segoe UI", 10, "bold"),
            borderwidth=0,
            padding=(16, 10),
        )
        style.map("Primary.TButton", background=[("active", accent_hover)])

        style.configure(
            "TCombobox",
            fieldbackground="#3f3f46",
            background="#3f3f46",
            foreground="#ffffff",
            arrowcolor="#ffffff",
            padding=5,
        )

    def _build_ui(self):
        container = ttk.Frame(self.root, padding=20)
        container.pack(fill="both", expand=True)

        # Header
        ttk.Label(container, text="Legendary Items Model Importer", style="Header.TLabel").pack(anchor="w")
        ttk.Label(
            container,
            text="Pick a .json model or .png texture from any extracted pack to copy, rename, and repack.",
            style="Sub.TLabel",
        ).pack(anchor="w", pady=(2, 14))

        # Step 1: Destination Pack Folder
        card1 = ttk.Frame(container, style="Card.TFrame", padding=12)
        card1.pack(fill="x", pady=(0, 10))

        row1 = ttk.Frame(card1, style="Card.TFrame")
        row1.pack(fill="x")
        ttk.Label(row1, text="1. Destination Pack Folder", style="CardTitle.TLabel").pack(side="left")
        ttk.Button(row1, text="Change Folder...", style="Secondary.TButton", command=self._change_dest_pack).pack(
            side="right"
        )

        self.dest_var = tk.StringVar(value=str(self.dest_pack))
        ttk.Label(card1, textvariable=self.dest_var, style="CardText.TLabel", wraplength=510).pack(
            anchor="w", pady=(4, 0)
        )

        # Step 2: Source Model / Texture Picker
        card2 = ttk.Frame(container, style="Card.TFrame", padding=12)
        card2.pack(fill="x", pady=(0, 10))

        row2 = ttk.Frame(card2, style="Card.TFrame")
        row2.pack(fill="x")
        ttk.Label(row2, text="2. Source Weapon File (.json or .png)", style="CardTitle.TLabel").pack(side="left")
        ttk.Button(row2, text="Browse File...", style="Secondary.TButton", command=self._pick_source_file).pack(
            side="right"
        )

        self.pair_status_var = tk.StringVar(value="No file selected yet.")
        self.pair_label = ttk.Label(card2, textvariable=self.pair_status_var, style="CardText.TLabel", wraplength=510)
        self.pair_label.pack(anchor="w", pady=(6, 0))

        # Step 3: Target Weapon ID Dropdown (populated from items/*.json)
        card3 = ttk.Frame(container, style="Card.TFrame", padding=12)
        card3.pack(fill="x", pady=(0, 16))

        ttk.Label(
            card3,
            text="3. Target Weapon ID (from assets/legendary/items/*.json)",
            style="CardTitle.TLabel",
        ).pack(anchor="w", pady=(0, 6))

        options = get_existing_item_ids(self.dest_pack)
        self.weapon_var = tk.StringVar(value=options[0] if options else "astral_sovereign")
        self.weapon_combo = ttk.Combobox(card3, textvariable=self.weapon_var, values=options, font=("Segoe UI", 10))
        self.weapon_combo.pack(fill="x")

        # Bottom Action Button
        ttk.Button(
            container,
            text="Import & Rebuild LegendaryItemsPack.zip",
            style="Primary.TButton",
            command=self._run_import,
        ).pack(fill="x")

    def _change_dest_pack(self):
        chosen = filedialog.askdirectory(title="Select Your LegendaryItemsPack Folder")
        if chosen:
            self.dest_pack = Path(chosen)
            self.dest_var.set(str(self.dest_pack))
            options = get_existing_item_ids(self.dest_pack)
            self.weapon_combo["values"] = options
            if options:
                self.weapon_var.set(options[0])

    def _pick_source_file(self):
        picked_file = filedialog.askopenfilename(
            title="Select Either the Model (.json) OR Texture (.png) File",
            filetypes=[
                ("Model JSON or Texture PNG", "*.json *.png"),
                ("Model JSON", "*.json"),
                ("Texture PNG", "*.png"),
            ],
        )
        if not picked_file:
            return

        picked_path = Path(picked_file)
        assets_root = find_assets_root(picked_path)
        if not assets_root:
            messagebox.showerror(
                "Invalid Folder",
                "Could not find an 'assets' folder above the selected file.\n"
                "Extract the source resource pack first and select a file inside its assets/ directory.",
            )
            return

        rel_to_assets = picked_path.relative_to(assets_root)
        if len(rel_to_assets.parts) < 2:
            messagebox.showerror("Invalid Structure", "Selected file is not inside a valid namespace folder.")
            return

        src_namespace = rel_to_assets.parts[0]
        stem_name = picked_path.stem

        src_model = None
        src_png = None

        if picked_path.suffix.lower() == ".json":
            src_model = picked_path
            candidate_png = assets_root / src_namespace / "textures" / "item" / f"{stem_name}.png"
            if not candidate_png.exists():
                tex_dir = assets_root / src_namespace / "textures"
                if tex_dir.exists():
                    matches = list(tex_dir.rglob(f"{stem_name}.png"))
                    candidate_png = matches[0] if matches else None
            src_png = candidate_png
        else:
            src_png = picked_path
            candidate_model = assets_root / src_namespace / "models" / "item" / f"{stem_name}.json"
            if not candidate_model.exists():
                mod_dir = assets_root / src_namespace / "models"
                if mod_dir.exists():
                    matches = list(mod_dir.rglob(f"{stem_name}.json"))
                    if not matches:
                        for mj in mod_dir.rglob("*.json"):
                            try:
                                if stem_name in mj.read_text(encoding="utf-8", errors="ignore"):
                                    matches.append(mj)
                                    break
                            except OSError:
                                pass
                    candidate_model = matches[0] if matches else None
            src_model = candidate_model

        if not src_model or not src_model.exists():
            messagebox.showerror(
                "Missing Model JSON",
                f"Found '{picked_path.name}', but could not locate a matching model .json inside:\n"
                f"{assets_root / src_namespace / 'models'}",
            )
            return

        self.assets_root = assets_root
        self.src_model = src_model
        self.src_png = src_png

        png_label = src_png.name if (src_png and src_png.exists()) else "(referenced inside JSON)"
        self.pair_status_var.set(f"Model: {src_model.name}   |   Texture: {png_label}")
        self.pair_label.configure(style="Match.TLabel")

    def _run_import(self):
        if not self.src_model or not self.assets_root:
            messagebox.showwarning("No File Selected", "Please select a source .json or .png file first.")
            return

        weapon_id = self.weapon_var.get().strip().lower().replace(" ", "_")
        if not weapon_id:
            messagebox.showwarning("Missing Weapon ID", "Please choose or enter a valid weapon ID.")
            return

        dest_tex_dir = self.dest_pack / "assets" / "legendary" / "textures" / "item"
        dest_mod_dir = self.dest_pack / "assets" / "legendary" / "models" / "item"
        dest_itm_dir = self.dest_pack / "assets" / "legendary" / "items"
        dest_tex_dir.mkdir(parents=True, exist_ok=True)
        dest_mod_dir.mkdir(parents=True, exist_ok=True)
        dest_itm_dir.mkdir(parents=True, exist_ok=True)

        try:
            with open(self.src_model, "r", encoding="utf-8") as f:
                model_data = json.load(f)
        except Exception as e:
            messagebox.showerror("JSON Error", f"Failed to parse {self.src_model.name}:\n{e}")
            return

        textures = model_data.get("textures", {})
        copied_textures = []
        updated_textures = {}
        tex_counter = 0

        for key, tex_ref in textures.items():
            if not isinstance(tex_ref, str) or tex_ref.startswith("#"):
                updated_textures[key] = tex_ref
                continue

            if ":" in tex_ref:
                ns, rel_tex = tex_ref.split(":", 1)
            else:
                ns, rel_tex = "minecraft", tex_ref

            ref_png = self.assets_root / ns / "textures" / f"{rel_tex}.png"
            ref_mcmeta = self.assets_root / ns / "textures" / f"{rel_tex}.png.mcmeta"

            if not ref_png.exists() and self.src_png and self.src_png.exists():
                ref_png = self.src_png
                ref_mcmeta = self.src_png.with_name(f"{self.src_png.name}.mcmeta")

            if ref_png.exists():
                suffix = "" if tex_counter == 0 else f"_{tex_counter}"
                new_tex_name = f"{weapon_id}{suffix}"
                shutil.copy2(ref_png, dest_tex_dir / f"{new_tex_name}.png")
                if ref_mcmeta.exists():
                    shutil.copy2(ref_mcmeta, dest_tex_dir / f"{new_tex_name}.png.mcmeta")
                updated_textures[key] = f"legendary:item/{new_tex_name}"
                if f"{new_tex_name}.png" not in copied_textures:
                    copied_textures.append(f"{new_tex_name}.png")
                tex_counter += 1
            else:
                updated_textures[key] = tex_ref

        if not textures and self.src_png and self.src_png.exists():
            shutil.copy2(self.src_png, dest_tex_dir / f"{weapon_id}.png")
            mcmeta = self.src_png.with_name(f"{self.src_png.name}.mcmeta")
            if mcmeta.exists():
                shutil.copy2(mcmeta, dest_tex_dir / f"{weapon_id}.png.mcmeta")
            updated_textures["layer0"] = f"legendary:item/{weapon_id}"
            copied_textures.append(f"{weapon_id}.png")

        if updated_textures:
            model_data["textures"] = updated_textures

        with open(dest_mod_dir / f"{weapon_id}.json", "w", encoding="utf-8") as f:
            json.dump(model_data, f, indent=2)

        client_item_data = {
            "model": {
                "type": "minecraft:model",
                "model": f"legendary:item/{weapon_id}",
            }
        }
        with open(dest_itm_dir / f"{weapon_id}.json", "w", encoding="utf-8") as f:
            json.dump(client_item_data, f, indent=2)

        mcmeta_path = self.dest_pack / "pack.mcmeta"
        if not mcmeta_path.exists():
            mcmeta_data = {
                "pack": {
                    "pack_format": 88,
                    "supported_formats": [46, 99],
                    "description": "Legendary Weapons (com.l413)",
                }
            }
            with open(mcmeta_path, "w", encoding="utf-8") as f:
                json.dump(mcmeta_data, f, indent=2)

        zip_out = self.dest_pack.parent / f"{self.dest_pack.name}.zip"
        with zipfile.ZipFile(zip_out, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.write(mcmeta_path, "pack.mcmeta")
            for file_path in (self.dest_pack / "assets").rglob("*"):
                if file_path.is_file():
                    zf.write(file_path, file_path.relative_to(self.dest_pack))

        self.weapon_combo["values"] = get_existing_item_ids(self.dest_pack)

        messagebox.showinfo(
            "Import Complete!",
            f"Successfully imported '{weapon_id}'!\n\n"
            f"• Model: assets/legendary/models/item/{weapon_id}.json\n"
            f"• Client Item: assets/legendary/items/{weapon_id}.json\n"
            f"• Textures copied: {', '.join(copied_textures) if copied_textures else 'None'}\n"
            f"• Clean ZIP built at:\n{zip_out}",
        )


if __name__ == "__main__":
    root = tk.Tk()
    app = WeaponImporterApp(root)
    root.mainloop()
