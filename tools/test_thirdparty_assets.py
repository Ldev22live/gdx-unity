"""Tests for tools/thirdparty_assets.py. Run from the repository root: python -m unittest tools/test_thirdparty_assets.py"""
import os
import stat
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import thirdparty_assets as tool  # noqa: E402


class SafePathTest(unittest.TestCase):
    def test_should_accept_plain_relative_paths(self):
        self.assertTrue(tool.safe_relative("textures/brick_diff_1k.jpg"))
        self.assertTrue(tool.safe_relative("door.gltf"))

    def test_should_refuse_paths_that_leave_the_folder(self):
        for path in ("../evil.png", "a/../../b.png", "/etc/passwd", "\\x.png", "C:/x.png", "a//b.png", "./a.png", ""):
            self.assertFalse(tool.safe_relative(path), path)


class ArchiveTest(unittest.TestCase):
    def test_should_refuse_executables_links_and_escaping_entries(self):
        problems = tool.archive_problems([
            ("pack/model.glb", None), ("pack/run.exe", None), ("../outside.png", None),
            ("pack/link", stat.S_IFLNK | 0o777), ("pack/", None)])
        self.assertEqual(3, len(problems), problems)
        self.assertTrue(any("run.exe" in p for p in problems))
        self.assertTrue(any("outside.png" in p for p in problems))
        self.assertTrue(any(p.startswith("link") for p in problems))

    def test_should_report_unsupported_formats_without_refusing_them(self):
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "pack.zip")
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("Models/tree.glb", b"glTF")
                archive.writestr("Models/tree.fbx", b"x")
                archive.writestr("License.txt", b"CC0")
            result = tool.check_archive(path)
        self.assertEqual([], result["problems"])
        self.assertEqual(3, result["files"])
        self.assertEqual([".fbx"], result["unsupportedExtensions"])


class PolyHavenPlanTest(unittest.TestCase):
    FILES = {
        "gltf": {"1k": {"gltf": {"url": "https://dl.polyhaven.org/file/x/door_1k.gltf", "md5": "a",
                                 "include": {"door.bin": {"url": "u1", "md5": "b"},
                                             "textures/door_diff_1k.jpg": {"url": "u2", "md5": "c"}}}}},
        "Diffuse": {"1k": {"jpg": {"url": "https://x/brick_diff_1k.jpg", "md5": "d"}}},
        "nor_gl": {"1k": {"jpg": {"url": "https://x/brick_nor_gl_1k.jpg", "md5": "e"}}},
        "arm": {"1k": {"jpg": {"url": "https://x/brick_arm_1k.jpg", "md5": "f"}}},
    }

    def test_should_plan_a_model_with_its_buffer_and_textures(self):
        plan = tool.polyhaven_plan(self.FILES, "model")
        self.assertEqual(["door_1k.gltf", "door.bin", "textures/door_diff_1k.jpg"], [p[0] for p in plan])

    def test_should_plan_the_three_texture_maps(self):
        plan = tool.polyhaven_plan(self.FILES, "texture")
        self.assertEqual(["brick_diff_1k.jpg", "brick_nor_gl_1k.jpg", "brick_arm_1k.jpg"], [p[0] for p in plan])

    def test_should_refuse_an_unsafe_include_from_the_api(self):
        files = {"gltf": {"1k": {"gltf": {"url": "https://x/a.gltf", "md5": "a",
                                          "include": {"../../evil.bin": {"url": "u", "md5": "b"}}}}}}
        with self.assertRaises(ValueError):
            tool.polyhaven_plan(files, "model")


class InstallTest(unittest.TestCase):
    def test_should_copy_the_files_and_replace_the_entry_with_the_same_id(self):
        import hashlib
        import json
        with tempfile.TemporaryDirectory() as folder:
            download = os.path.join(folder, "dl")
            os.makedirs(os.path.join(download, "textures"))
            data = b"jpeg bytes"
            with open(os.path.join(download, "textures", "a_diff_1k.jpg"), "wb") as out:
                out.write(data)
            fetched = {"slug": "red_brick_03", "name": "Red Brick 03", "authors": ["Rob Tuytel"],
                       "source": "https://polyhaven.com/a/red_brick_03",
                       "files": [{"path": "textures/a_diff_1k.jpg", "file": "a_diff_1k.jpg", "bytes": len(data),
                                  "sha256": hashlib.sha256(data).hexdigest()}]}
            fetch_json = os.path.join(folder, "fetch.json")
            with open(fetch_json, "w", encoding="utf-8") as out:
                json.dump(fetched, out)
            manifest = os.path.join(folder, "assets", "licenses", "manifest.json")

            tool.install(fetch_json, download, "texture", "textures/thirdparty/polyhaven/red_brick_03", "jpg", "2026-11-19", manifest)
            entry = tool.install(fetch_json, download, "texture", "textures/thirdparty/polyhaven/red_brick_03", "jpg", "2026-11-19",
                                 manifest)

            with open(manifest, encoding="utf-8") as source:
                written = json.load(source)
            self.assertEqual(1, len(written["assets"]), "replaced, not added twice")
            self.assertEqual("polyhaven-red-brick-03", entry["id"])
            self.assertEqual("CC0-1.0", entry["licence"])
            self.assertEqual("textures/thirdparty/polyhaven/red_brick_03/textures/a_diff_1k.jpg", entry["files"][0]["path"])
            self.assertTrue(os.path.isfile(os.path.join(folder, "assets", *entry["files"][0]["path"].split("/"))))

    def test_should_refuse_a_file_changed_since_it_was_fetched(self):
        import json
        with tempfile.TemporaryDirectory() as folder:
            with open(os.path.join(folder, "a.jpg"), "wb") as out:
                out.write(b"changed")
            fetch_json = os.path.join(folder, "fetch.json")
            with open(fetch_json, "w", encoding="utf-8") as out:
                json.dump({"slug": "x", "name": "X", "authors": [], "source": "s",
                           "files": [{"path": "a.jpg", "file": "a.jpg", "bytes": 1, "sha256": "0" * 64}]}, out)
            with self.assertRaises(ValueError):
                tool.install(fetch_json, folder, "texture", "textures/x", "jpg", "2026-11-19",
                             os.path.join(folder, "assets", "licenses", "manifest.json"))


if __name__ == "__main__":
    unittest.main()
