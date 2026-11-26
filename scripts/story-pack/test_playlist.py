#!/usr/bin/env python3
"""Unit-Tests für extract_playlist.py und extract_script.cleanup_downloaded_video."""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import extract_playlist as ep  # noqa: E402
import extract_script as es  # noqa: E402


class TestPlaylistAndCleanup(unittest.TestCase):
    def test_parse_flat_playlist_lines_handles_durations_and_na(self) -> None:
        stdout = (
            "01\tCxwecy8pOas\t2248\tPOKÉMON POKOPIA #1\n"
            "02\t2hRty3TXSDg\t1978.0\tPOKÉMON POKOPIA #2\n"
            "03\tNA\t120\tungültig\n"
            "\n"
            "28\twYeDkXas8jA\tNA\tFinale\n"
        )
        items = ep.parse_flat_playlist_lines(stdout)
        self.assertEqual(3, len(items))
        self.assertEqual(ep.PlaylistItem(1, "Cxwecy8pOas", 2248, "POKÉMON POKOPIA #1"), items[0])
        self.assertEqual(ep.PlaylistItem(2, "2hRty3TXSDg", 1978, "POKÉMON POKOPIA #2"), items[1])
        self.assertEqual(ep.PlaylistItem(28, "wYeDkXas8jA", None, "Finale"), items[2])

    def test_select_items_inclusive_range(self) -> None:
        items = [
            ep.PlaylistItem(1, "id1", 100, "E1"),
            ep.PlaylistItem(2, "id2", 200, "E2"),
            ep.PlaylistItem(3, "id3", 300, "E3"),
            ep.PlaylistItem(4, "id4", 400, "E4"),
        ]
        self.assertEqual([2, 3], [i.index for i in ep.select_items(items, 2, 3)])
        self.assertEqual([3, 4], [i.index for i in ep.select_items(items, 3, None)])

    def test_episode_dir_and_should_skip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ep_dir = ep.episode_dir(root, "domtendo", 2)
            self.assertEqual(root / "domtendo-002", ep_dir)
            self.assertFalse(ep.should_skip(ep_dir, force=False))

            ep_dir.mkdir(parents=True)
            script = ep_dir / "script.jsonl"
            script.write_text("", encoding="utf-8")
            self.assertFalse(ep.should_skip(ep_dir, force=False))

            script.write_text('{"text":"Hallo"}\n', encoding="utf-8")
            self.assertTrue(ep.should_skip(ep_dir, force=False))
            self.assertFalse(ep.should_skip(ep_dir, force=True))

    def test_cleanup_downloaded_video_respects_external_and_parent(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            out_dir = base / "ep002"
            other_dir = base / "external"
            out_dir.mkdir()
            other_dir.mkdir()

            own_video = out_dir / "video.mp4"
            own_video.write_bytes(b"video")
            ext_video = other_dir / "source.mp4"
            ext_video.write_bytes(b"ext")

            # Externe Quellen oder fremde Ordner werden nie gelöscht
            self.assertFalse(es.cleanup_downloaded_video(own_video, out_dir, external_video=True))
            self.assertTrue(own_video.is_file())

            self.assertFalse(es.cleanup_downloaded_video(ext_video, out_dir, external_video=False))
            self.assertTrue(ext_video.is_file())

            # Heruntergeladenes Video in out_dir wird gelöscht
            self.assertTrue(es.cleanup_downloaded_video(own_video, out_dir, external_video=False))
            self.assertFalse(own_video.exists())


if __name__ == "__main__":
    unittest.main()
