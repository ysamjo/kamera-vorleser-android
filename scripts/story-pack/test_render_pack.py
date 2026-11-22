#!/usr/bin/env python3
"""Unit-Tests für render_pack.py (ohne echte HTTP-Requests)."""

import json
import sys
import tempfile
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import render_pack as rp  # noqa: E402


class TestRenderPack(unittest.TestCase):
    def test_clean_speaker_maps_noisy_labels_and_drops_garbage(self) -> None:
        self.assertEqual("Professor Tangoloss", rp.clean_speaker("Tangoloss ar"))
        self.assertEqual("Professor Tangoloss", rp.clean_speaker("Zu Tangoloss"))
        self.assertEqual("Professor Tangoloss", rp.clean_speaker("ei Tangoloss ut"))
        self.assertEqual("Professor Tangoloss", rp.clean_speaker("\"Professor ;"))
        self.assertEqual("Professor Tangoloss", rp.clean_speaker("Professor een"))
        self.assertEqual("Schiggy", rp.clean_speaker("Schiggy un"))
        self.assertEqual("Bisasam", rp.clean_speaker("Er Bisasam"))
        self.assertEqual("Bisasam", rp.clean_speaker("Bisasam >7"))

        for garbage in ("nz", "En", "IN", "- ' nm", "en = Ex", "Steuerung des"):
            self.assertEqual("Erzähler", rp.clean_speaker(garbage), garbage)

    def test_clean_dialogue_text_fixes_tesseract_artifacts(self) -> None:
        self.assertEqual(
            "Oh, das ist ja ein Pokédex. Und dieses Modell hat sogar eine Kameral",
            rp.clean_dialogue_text("Oh, das ist ja ein Pok&dex. Und dieses Modell hat sogar eine Kameral v"),
        )
        self.assertEqual(
            "Ich bin mit einem Pokémon befreundet!",
            rp.clean_dialogue_text("Ich bin mit einem Pok&mon befreundet! )"),
        )
        self.assertEqual(
            "Das nennt man ein Lagerfeuer.",
            rp.clean_dialogue_text("Das nennt man ein & Lagerfeuer."),
        )
        self.assertEqual(
            "Du hast den Baumstammstuhl verschluckt!",
            rp.clean_dialogue_text("Du hast den ® Baumstammstuhl verschluckt!"),
        )
        self.assertEqual(
            "Das ist eine Werkbank.",
            rp.clean_dialogue_text("Das ist eine $ \\Werkbank."),
        )

    def test_prepare_entries_filters_noise_and_merges_cleaned_twins(self) -> None:
        records = [
            {"speaker": "Erzähler", "text": "fe}", "first_line": "fe}", "confidence": 0.33},
            {"speaker": "- ' nm", "text": "Info ausblenden Hand wechseln", "first_line": "Info ausblenden", "confidence": 0.96},
            {"speaker": "Erzähler", "text": "@ Aufwecken", "first_line": "@ Aufwecken", "confidence": 0.87},
            {"speaker": "nz", "text": "Und wenn meinereiner sich nicht täuscht, kannst du jetzt helfen!", "first_line": "Und wenn meinereiner sich nicht täuscht,", "confidence": 0.95},
            {"speaker": "Professor Tangoloss", "text": "Und wenn meinereiner sich nicht täuscht, kannst du jetzt helfen!", "first_line": "Und wenn meinereiner sich nicht täuscht,", "confidence": 0.94},
            {"speaker": "Professor Tangoloss", "text": "Oh, ein & Lagerfeuer! =", "first_line": "Oh, ein & Lagerfeuer! =", "confidence": 0.94},
        ]
        with tempfile.TemporaryDirectory() as tmp:
            script = Path(tmp) / "script.jsonl"
            script.write_text("\n".join(json.dumps(r) for r in records) + "\n", encoding="utf-8")

            entries, stats = rp.prepare_entries([script])
            self.assertEqual(6, stats["raw_count"])
            self.assertEqual(3, stats["filtered_noise"])
            self.assertEqual(1, stats["merged_duplicates"])
            self.assertEqual(2, stats["final_count"])

            self.assertEqual("dlg_0001", entries[0].id)
            self.assertEqual("Professor Tangoloss", entries[0].speaker)
            self.assertEqual("dlg_0002", entries[1].id)
            self.assertEqual("Oh, ein Lagerfeuer!", entries[1].text)

    def test_build_fish_payload_uses_15_roles_and_directions(self) -> None:
        entry = rp.PackEntry(
            id="dlg_0001",
            speaker="Bisasam",
            speaker_norm="bisasam",
            role_slot=7,
            text="Der Boden hier ist pupstrocken.",
            text_norm="der boden hier ist pupstrocken",
            first_line="Der Boden hier ist pupstrocken.",
            first_line_norm="der boden hier ist pupstrocken",
            confidence=0.95,
            surface="GRAY",
        )
        payload = rp.build_fish_payload(entry, speed=1.1, apply_dict=False)
        self.assertEqual(rp.FISH_VOICES[7], payload["reference_id"])
        self.assertIn("sanft, naturverbunden", payload["text"])
        self.assertTrue(payload["text"].endswith("Der Boden hier ist pupstrocken."))
        self.assertEqual(24000, payload["sample_rate"])
        self.assertEqual(1.1, payload["prosody"]["speed"])

    def test_synthesize_fish_pcm_switches_to_free_model_on_402(self) -> None:
        entry = rp.PackEntry(
            id="dlg_0001", speaker="Erzähler", speaker_norm="erzähler", role_slot=0,
            text="Hallo Pokopia!", text_norm="hallo pokopia",
            first_line="Hallo Pokopia!", first_line_norm="hallo pokopia",
            confidence=0.99, surface="BLUE",
        )
        calls = []

        def fake_post(url: str, headers: dict, body: bytes):
            calls.append(headers["model"])
            if headers["model"] == "drama-3":
                return 402, b"payment required"
            return 200, b"\x01\x00\x02\x00"

        state = {"model": "drama-3"}
        pcm = rp.synthesize_fish_pcm(
            entry=entry,
            api_key="test-key",
            speed=1.0,
            apply_dict=False,
            state=state,
            state_lock=threading.Lock(),
            allow_free_fallback=True,
            http_post=fake_post,
        )
        self.assertEqual(["drama-3", "s2.1-pro-free"], calls)
        self.assertEqual("s2.1-pro-free", state["model"])
        self.assertEqual(b"\x01\x00\x02\x00", pcm)

    def test_write_index_jsonl_matches_android_index_schema(self) -> None:
        entry = rp.PackEntry(
            id="dlg_0001", speaker="Pikachu", speaker_norm="pikachu", role_slot=1,
            text="Hallo! Bereit für das Abenteuer?", text_norm="hallo bereit für das abenteuer",
            first_line="Hallo!", first_line_norm="hallo", confidence=0.99, surface="GRAY",
        )
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "index.jsonl"
            rp.write_index_jsonl([entry], out)
            parsed = json.loads(out.read_text(encoding="utf-8").strip())
            self.assertEqual({
                "id": "dlg_0001",
                "speaker": "Pikachu",
                "text": "Hallo! Bereit für das Abenteuer?",
                "first_line": "Hallo!",
                "audio": "audio/dlg_0001.ogg",
            }, parsed)


if __name__ == "__main__":
    unittest.main()
