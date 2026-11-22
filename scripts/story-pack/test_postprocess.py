#!/usr/bin/env python3
"""Unit-Tests für dialogue_postprocess.py (nur Python-Stdlib)."""

import sys
import unicodedata
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dialogue_postprocess as dp  # noqa: E402


class TestPostprocess(unittest.TestCase):
    def test_normalize_nfc_nfd_umlauts(self) -> None:
        nfd = unicodedata.normalize("NFD", "ÄÖÜß · Grüße vom Professor Tangoloss:")
        self.assertEqual("äöüß grüße vom professor tangoloss", dp.normalize(nfd))
        self.assertEqual("große straße 12", dp.normalize("Große_Straße-12!"))

    def test_assemble_story_text_and_first_line(self) -> None:
        # Permutierte Zeilen inkl. geteilter erster Zeile und Silbentrennung
        lines = [
            dp.OcrLine("rascht sein!", 400, 920, 700, 950, surface="BLUE"),
            dp.OcrLine("über-", 950, 860, 1120, 890, surface="BLUE"),
            dp.OcrLine("Du wirst", 400, 862, 650, 892, surface="BLUE"),
        ]
        self.assertEqual("Du wirst überrascht sein!", dp.assemble_story_text(lines))
        self.assertEqual("Du wirst über-", dp.extract_first_line(lines))

    def test_detect_speaker_normal_split_and_ui_rejection(self) -> None:
        story = dp.OcrLine("Schön, dass du hier bist!", 430, 880, 1100, 915, surface="GRAY")
        # Normales Namensschild
        pikachu = dp.OcrLine("Pikachu", 430, 800, 560, 835, surface="NONE")
        self.assertEqual("Pikachu", dp.detect_speaker([pikachu, story], [story], 1920, 1080))

        # Zweigeteiltes "Professor" + "Tangoloss" (nebeneinander und übereinander)
        prof = dp.OcrLine("Professor", 430, 800, 580, 835, surface="NONE")
        tango = dp.OcrLine("Tangoloss", 590, 800, 740, 835, surface="NONE")
        self.assertEqual("Professor Tangoloss", dp.detect_speaker([prof, tango, story], [story], 1920, 1080))

        prof_top = dp.OcrLine("Professor", 430, 765, 580, 795, surface="NONE")
        tango_bot = dp.OcrLine("Tangoloss:", 430, 800, 600, 835, surface="NONE")
        self.assertEqual("Professor Tangoloss", dp.detect_speaker([prof_top, tango_bot, story], [story], 1920, 1080))

        # UI-Begriffe und Dialogsätze werden abgewiesen
        for bad in ("Einstellungen", "Inventar", "Hallo! Wie geht es?", "Ich bin bereit"):
            bad_line = dp.OcrLine(bad, 430, 800, 700, 835, surface="NONE")
            self.assertIsNone(dp.detect_speaker([bad_line, story], [story], 1920, 1080), bad)

    def test_gray_rejected_without_character_label_while_blue_kept(self) -> None:
        gray_record = {
            "frame_index": 10,
            "timestamp_sec": 5.0,
            "width": 1920,
            "height": 1080,
            "lines": [
                {"text": "Einstellungen", "left": 430, "top": 800, "right": 660, "bottom": 835, "surface": "NONE"},
                {"text": "Hier kannst du die Lautstärke ändern.", "left": 430, "top": 880, "right": 1200, "bottom": 915, "surface": "GRAY"},
            ],
        }
        _, _, parsed_gray = dp.parse_raw_frame(gray_record)
        self.assertIsNone(parsed_gray)

        blue_record = {
            "frame_index": 11,
            "timestamp_sec": 5.5,
            "width": 1920,
            "height": 1080,
            "lines": [
                {"text": "Willkommen in der Welt von Pokopia!", "left": 430, "top": 880, "right": 1200, "bottom": 915, "surface": "BLUE"},
            ],
        }
        _, _, parsed_blue = dp.parse_raw_frame(blue_record)
        self.assertIsNotNone(parsed_blue)
        self.assertEqual("Erzähler", parsed_blue.speaker)
        self.assertEqual("Willkommen in der Welt von Pokopia!", parsed_blue.text)

    def test_merge_frames_typewriter_growth(self) -> None:
        raw_frames = [
            (1, 10.0, dp.FrameDialogue(1, 10.0, "Erzähler", False, "Hallo, ich", "hallo ich", "Hallo, ich", "hallo ich", "GRAY", 0.9)),
            (2, 10.5, dp.FrameDialogue(2, 10.5, "Professor Tangoloss", True, "Hallo, ich bin", "hallo ich bin", "Hallo, ich bin", "hallo ich bin", "GRAY", 0.92)),
            (3, 11.0, dp.FrameDialogue(3, 11.0, "Professor Tangoloss", True, "Hallo, ich bin Tangoloss!", "hallo ich bin tangoloss", "Hallo, ich bin Tangoloss!", "hallo ich bin tangoloss", "GRAY", 0.96)),
        ]
        segments = dp.merge_frames(raw_frames)
        self.assertEqual(1, len(segments))
        self.assertEqual("Professor Tangoloss", segments[0].speaker)
        self.assertEqual("Hallo, ich bin Tangoloss!", segments[0].text)
        self.assertEqual(10.0, segments[0].start_sec)
        self.assertEqual(11.0, segments[0].end_sec)

    def test_deduplicate_segments_merges_repeats(self) -> None:
        seg1 = dp.DialogueSegment(
            start_sec=10.0, end_sec=12.0,
            text="Schön, dass du wieder da bist!",
            text_norm="schön dass du wieder da bist",
            first_line="Schön, dass du wieder da bist!",
            first_line_norm="schön dass du wieder da bist",
            surface="GRAY", confidence=0.95,
        )
        seg1.add_speaker("Pikachu", True)

        seg2 = dp.DialogueSegment(
            start_sec=120.0, end_sec=122.0,
            text="Schön, dass du wieder da bist!",
            text_norm="schön dass du wieder da bist",
            first_line="Schön, dass du wieder da bist!",
            first_line_norm="schön dass du wieder da bist",
            surface="GRAY", confidence=0.98,
        )
        seg2.add_speaker("Pikachu", True)

        unique = dp.deduplicate_segments([seg1, seg2])
        self.assertEqual(1, len(unique))
        self.assertEqual(2, len(unique[0].occurrences))
        self.assertEqual(1, unique[0].role_slot)

    def test_analyze_prefixes_unique_and_ambiguous(self) -> None:
        items = [
            dp.UniqueDialogue("dlg_0001", "Pikachu", "pikachu", 1, "Pikachu",
                              "Das ist ein ganz besonderer Tag im Tal! Komm mit!",
                              "das ist ein ganz besonderer tag im tal komm mit",
                              "Das ist ein ganz besonderer Tag im Tal!",
                              "das ist ein ganz besonderer tag im tal",
                              "GRAY", 0.95, 1.0, 2.0, [{"start_sec": 1.0, "end_sec": 2.0}]),
            dp.UniqueDialogue("dlg_0002", "Pikachu", "pikachu", 1, "Pikachu",
                              "Wir bauen heute eine große Brücke über den Fluss.",
                              "wir bauen heute eine große brücke über den fluss",
                              "Wir bauen heute eine große Brücke",
                              "wir bauen heute eine große brücke",
                              "GRAY", 0.95, 5.0, 6.0, [{"start_sec": 5.0, "end_sec": 6.0}]),
            dp.UniqueDialogue("dlg_0003", "Schiggy", "schiggy", 11, "Schiggy",
                              "Wir bauen heute eine große Brücke für alle Freunde.",
                              "wir bauen heute eine große brücke für alle freunde",
                              "Wir bauen heute eine große Brücke",
                              "wir bauen heute eine große brücke",
                              "GRAY", 0.95, 10.0, 11.0, [{"start_sec": 10.0, "end_sec": 11.0}]),
            dp.UniqueDialogue("dlg_0004", "Erzähler", "erzähler", 0, "Erzähler",
                              "Kurz!", "kurz", "Kurz!", "kurz",
                              "BLUE", 0.95, 15.0, 16.0, [{"start_sec": 15.0, "end_sec": 16.0}]),
        ]
        stats = dp.analyze_prefixes(items)
        self.assertEqual(4, stats["total_unique"])
        self.assertEqual(3, stats["qualifying_count"])
        self.assertEqual(1, stats["unique_global_count"])
        self.assertEqual(3, stats["unique_per_speaker_count"])
        self.assertEqual(1, len(stats["collisions"]))

    def test_merge_frames_continuous_box_with_pause_and_extension(self) -> None:
        raw_frames = [
            (1, 265.5, dp.FrameDialogue(
                1, 265.5, "Professor Tangoloss", True,
                "Oh? Was ist denn das für ein Geräusch? Irgendetwas piept...",
                "oh was ist denn das für ein geräusch irgendetwas piept",
                "Oh? Was ist denn das für ein Geräusch?",
                "oh was ist denn das für ein geräusch",
                "GRAY", 0.99,
            )),
            (2, 269.5, dp.FrameDialogue(
                2, 269.5, "Professor Tangoloss", True,
                "Oh? Was ist denn das für ein Geräusch? Irgendetwas piept... Aber was und wo...?",
                "oh was ist denn das für ein geräusch irgendetwas piept aber was und wo",
                "Oh? Was ist denn das für ein Geräusch?",
                "oh was ist denn das für ein geräusch",
                "GRAY", 0.99,
            )),
        ]
        segments = dp.merge_frames(raw_frames)
        self.assertEqual(1, len(segments))
        self.assertEqual(
            "Oh? Was ist denn das für ein Geräusch? Irgendetwas piept... Aber was und wo...?",
            segments[0].text,
        )

    def test_merge_frames_continuous_vs_reopened_boundary(self) -> None:
        dlg_a = dp.FrameDialogue(
            1, 10.0, "Professor Tangoloss", True,
            "Hm? Halt, irgendetwas stimmt hier aber nicht...",
            "hm halt irgendetwas stimmt hier aber nicht",
            "Hm? Halt, irgendetwas stimmt hier aber nicht...",
            "hm halt irgendetwas stimmt hier aber nicht",
            "GRAY", 0.99,
        )
        dlg_b = dp.FrameDialogue(
            2, 20.0, "Professor Tangoloss", True,
            "Hm? Halt, irgendetwas stimmt hier aber nicht...",
            "hm halt irgendetwas stimmt hier aber nicht",
            "Hm? Halt, irgendetwas stimmt hier aber nicht...",
            "hm halt irgendetwas stimmt hier aber nicht",
            "GRAY", 0.99,
        )
        # Ohne Grenz-Frame (Box 10 s durchgehend offen): 1 Segment
        continuous_segments = dp.merge_frames([(1, 10.0, dlg_a), (2, 20.0, dlg_b)])
        self.assertEqual(1, len(continuous_segments))

        # Mit Grenz-Frame (Box geschlossen und nach 10 s neu geöffnet): 2 Segmente -> 1 Unique mit 2 Vorkommen
        reopened_segments = dp.merge_frames([(1, 10.0, dlg_a), (2, 10.5, None), (3, 20.0, dlg_b)])
        self.assertEqual(2, len(reopened_segments))
        unique = dp.deduplicate_segments(reopened_segments)
        self.assertEqual(1, len(unique))
        self.assertEqual(2, len(unique[0].occurrences))


if __name__ == "__main__":
    unittest.main()
