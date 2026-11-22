import CoreGraphics
import Foundation
import ImageIO

enum BubbleSurface: String {
    case none = "NONE"
    case blue = "BLUE"
    case gray = "GRAY"
    case outlined = "OUTLINED"
}

struct OcrLineOutput: Encodable {
    let text: String
    let left: Int
    let top: Int
    let right: Int
    let bottom: Int
    let confidence: Double
    let surface: String
}

struct FrameOutput: Encodable {
    let frame_index: Int
    let timestamp_sec: Double
    let width: Int
    let height: Int
    let lines: [OcrLineOutput]
}

struct StatsOutput: Encodable {
    let total_frames: Int
    let skipped_unchanged: Int
    let skipped_changing: Int
    let skipped_no_bubble: Int
    let ocr_frames: Int
    let ocr_errors: Int
    let elapsed_sec: Double
}

struct Config {
    let framesDir: String
    let fullWidth: Int
    let fullHeight: Int
    let cropYRatio: Double
    let fps: Double
    let outPath: String
    let workers: Int
}

func parseArgs() -> Config {
    var framesDir: String?
    var fullWidth: Int?
    var fullHeight: Int?
    var cropYRatio = 0.60
    var fps = 2.0
    var outPath: String?
    var workers = max(1, min(6, ProcessInfo.processInfo.activeProcessorCount))

    var i = 1
    let args = CommandLine.arguments
    while i < args.count {
        let arg = args[i]
        func nextValue() -> String {
            guard i + 1 < args.count else {
                fputs("Fehlender Wert für Argument: \(arg)\n", stderr)
                exit(2)
            }
            i += 1
            return args[i]
        }
        switch arg {
        case "--frames-dir":
            framesDir = nextValue()
        case "--full-width":
            fullWidth = Int(nextValue())
        case "--full-height":
            fullHeight = Int(nextValue())
        case "--crop-y-ratio":
            cropYRatio = Double(nextValue()) ?? cropYRatio
        case "--fps":
            fps = Double(nextValue()) ?? fps
        case "--out":
            outPath = nextValue()
        case "--workers":
            if let parsed = Int(nextValue()), parsed > 0 {
                workers = parsed
            }
        default:
            fputs("Unbekanntes Argument: \(arg)\n", stderr)
            exit(2)
        }
        i += 1
    }

    guard let dir = framesDir, let fw = fullWidth, let fh = fullHeight, let out = outPath,
          fw > 0, fh > 0, fps > 0, cropYRatio >= 0, cropYRatio < 1 else {
        fputs("Verwendung: frame-ocr --frames-dir <dir> --full-width <int> --full-height <int> --crop-y-ratio <float> --fps <float> --out <path> [--workers <int>]\n", stderr)
        exit(2)
    }

    return Config(
        framesDir: dir,
        fullWidth: fw,
        fullHeight: fh,
        cropYRatio: cropYRatio,
        fps: fps,
        outPath: out,
        workers: workers
    )
}

final class CropImageBuffer {
    let width: Int
    let height: Int
    let cropTopPx: Int
    let fullWidth: Int
    let fullHeight: Int
    private let pixels: [UInt8] // RGBA, 4 Bytes pro Pixel, Row 0 = oberste Zeile

    init?(url: URL, cropTopPx: Int, fullWidth: Int, fullHeight: Int) {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            return nil
        }
        let w = image.width
        let h = image.height
        self.width = w
        self.height = h
        self.cropTopPx = cropTopPx
        self.fullWidth = fullWidth
        self.fullHeight = fullHeight

        var buffer = [UInt8](repeating: 0, count: w * h * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        let drew: Bool = buffer.withUnsafeMutableBytes { ptr in
            guard let baseAddress = ptr.baseAddress,
                  let ctx = CGContext(
                    data: baseAddress,
                    width: w,
                    height: h,
                    bitsPerComponent: 8,
                    bytesPerRow: w * 4,
                    space: colorSpace,
                    bitmapInfo: bitmapInfo
                  ) else {
                return false
            }
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard drew else { return nil }
        self.pixels = buffer
    }

    private func cropCoords(fullX: Int, fullY: Int) -> (Int, Int)? {
        let cx = fullWidth == width ? fullX : Int((Double(fullX) * Double(width) / Double(fullWidth)).rounded())
        let expectedCropH = max(1, fullHeight - cropTopPx)
        let relY = fullY - cropTopPx
        let cy = expectedCropH == height ? relY : Int((Double(relY) * Double(height) / Double(expectedCropH)).rounded())
        guard cx >= 0, cx < width, cy >= 0, cy < height else { return nil }
        return (cx, cy)
    }

    func rgbInt(fullX: Int, fullY: Int) -> Int {
        guard let (cx, cy) = cropCoords(fullX: fullX, fullY: fullY) else { return -1 }
        let idx = (cy * width + cx) * 4
        let r = Int(pixels[idx])
        let g = Int(pixels[idx + 1])
        let b = Int(pixels[idx + 2])
        return (r << 16) | (g << 8) | b
    }

    func rgbComponents(fullX: Int, fullY: Int) -> (Int, Int, Int)? {
        guard let (cx, cy) = cropCoords(fullX: fullX, fullY: fullY) else { return nil }
        let idx = (cy * width + cx) * 4
        return (Int(pixels[idx]), Int(pixels[idx + 1]), Int(pixels[idx + 2]))
    }

    func luma(fullX: Int, fullY: Int) -> Int {
        guard let (r, g, b) = rgbComponents(fullX: fullX, fullY: fullY) else { return -1 }
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    func dialogueFingerprint() -> [UInt8] {
        let cols = 64
        let rows = 16
        var fp = [UInt8](repeating: 0, count: cols * rows)
        let xStart = Double(fullWidth) * 0.21
        let xSpan = Double(fullWidth) * (0.79 - 0.21)
        let yStart = Double(fullHeight) * 0.74
        let ySpan = Double(fullHeight) * (0.98 - 0.74)

        for row in 0..<rows {
            let fy = Int((yStart + ySpan * (Double(row) + 0.5) / Double(rows)).rounded())
            for col in 0..<cols {
                let fx = Int((xStart + xSpan * (Double(col) + 0.5) / Double(cols)).rounded())
                let lum = luma(fullX: fx, fullY: fy)
                fp[row * cols + col] = UInt8(max(0, min(255, lum)))
            }
        }
        return fp
    }

    func hasBubbleCandidateColor() -> Bool {
        let cols = 12
        let rows = 4
        var matching = 0
        var total = 0
        let xStart = Double(fullWidth) * 0.25
        let xSpan = Double(fullWidth) * (0.75 - 0.25)
        let yStart = Double(fullHeight) * 0.80
        let ySpan = Double(fullHeight) * (0.95 - 0.80)

        for row in 0..<rows {
            let fy = Int((yStart + ySpan * (Double(row) + 0.5) / Double(rows)).rounded())
            for col in 0..<cols {
                let fx = Int((xStart + xSpan * (Double(col) + 0.5) / Double(cols)).rounded())
                guard let (r, g, b) = rgbComponents(fullX: fx, fullY: fy) else { continue }
                total += 1
                let isBlue = b > 70 && b > r + 24 && b > g + 8
                let maxC = max(r, max(g, b))
                let minC = min(r, min(g, b))
                let isGray = (maxC - minC) <= 30 && minC >= 65
                if isBlue || isGray {
                    matching += 1
                }
            }
        }
        guard total > 0 else { return false }
        return Double(matching) / Double(total) >= 0.25
    }
}

struct FrameDiff {
    let overallMAD: Double
    let maxTileMAD: Double
    var differ: Bool { overallMAD >= 0.6 || maxTileMAD >= 6.0 }
}

func framesDiffer(_ a: [UInt8], _ b: [UInt8]) -> FrameDiff {
    let cols = 64
    let rows = 16
    guard a.count == cols * rows, b.count == cols * rows else {
        return FrameDiff(overallMAD: 255.0, maxTileMAD: 255.0)
    }
    var totalSum = 0
    var maxTileMAD = 0.0
    for tileRow in 0..<4 {
        for tileCol in 0..<16 {
            var tileSum = 0
            for dr in 0..<4 {
                let r = tileRow * 4 + dr
                for dc in 0..<4 {
                    let c = tileCol * 4 + dc
                    let idx = r * cols + c
                    tileSum += abs(Int(a[idx]) - Int(b[idx]))
                }
            }
            totalSum += tileSum
            let tileMAD = Double(tileSum) / 16.0
            if tileMAD > maxTileMAD {
                maxTileMAD = tileMAD
            }
        }
    }
    let overallMAD = Double(totalSum) / Double(cols * rows)
    return FrameDiff(overallMAD: overallMAD, maxTileMAD: maxTileMAD)
}

// Exakte Portierung von BubbleAppearance.java
enum BubbleClassifier {
    static let rows = 5
    static let columns = 9

    static func inDialogueRegion(centerX: Double, centerY: Double) -> Bool {
        return centerX >= 0.21 && centerX <= 0.79 && centerY >= 0.74 && centerY <= 0.98
    }

    static func classifyDialogue(
        buffer: CropImageBuffer,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) -> BubbleSurface {
        let fullW = buffer.fullWidth
        let fullH = buffer.fullHeight
        let lineHeight = bottom - top
        let centerX = (left + right) / 2
        let centerY = (top + bottom) / 2
        let halfWidth = max((right - left) / 2 + lineHeight, Int(Double(fullW) * 0.10))
        let halfHeight = max(4, lineHeight)

        let sLeft = max(centerX - halfWidth, Int((Double(fullW) * 0.21).rounded()))
        let sRight = min(centerX + halfWidth, Int((Double(fullW) * 0.79).rounded()))
        let sTop = centerY - halfHeight
        let sBottom = centerY + halfHeight
        let aboveY = Int((Double(fullH) * 0.68).rounded())

        var pixels = [Int](repeating: -1, count: rows * columns)
        for r in 0..<rows {
            let py = sTop + max(1, sBottom - sTop) * r / (rows - 1)
            for c in 0..<columns {
                let px = sLeft + max(1, sRight - sLeft) * c / (columns - 1)
                pixels[r * columns + c] = buffer.rgbInt(fullX: px, fullY: py)
            }
        }

        var above = [Int](repeating: -1, count: columns)
        for c in 0..<columns {
            let px = sLeft + max(1, sRight - sLeft) * c / (columns - 1)
            above[c] = buffer.rgbInt(fullX: px, fullY: aboveY)
        }

        let surface = classify(pixels)
        if surface == .none || hasUpperBoundary(panel: pixels, above: above) {
            return surface
        }
        return hasCompactOutline(left: left, top: top, right: right, bottom: bottom, buffer: buffer)
            ? .outlined : .none
    }

    private static func classify(_ pixels: [Int]) -> BubbleSurface {
        guard pixels.count == rows * columns else { return .none }
        if isPanel(pixels, blue: true) { return .blue }
        if isPanel(pixels, blue: false) { return .gray }
        return .none
    }

    private static func isPanel(_ pixels: [Int], blue: Bool) -> Bool {
        var count = 0
        var leftHits = 0
        var rightHits = 0
        var topHits = 0
        var bottomHits = 0
        var brightnessValues = [Int]()
        brightnessValues.reserveCapacity(pixels.count)

        for row in 0..<rows {
            for col in 0..<columns {
                let pixel = pixels[row * columns + col]
                if pixel < 0 { continue }
                let r = (pixel >> 16) & 255
                let g = (pixel >> 8) & 255
                let b = pixel & 255
                let matches: Bool
                if blue {
                    matches = b > 70 && b > r + 24 && b > g + 8
                } else {
                    let maxC = max(r, max(g, b))
                    let minC = min(r, min(g, b))
                    matches = (maxC - minC) <= 30 && minC >= 65
                }
                if !matches { continue }
                count += 1
                if col < 2 { leftHits += 1 }
                if col > 6 { rightHits += 1 }
                if row == 0 { topHits += 1 }
                if row == rows - 1 { bottomHits += 1 }
                brightnessValues.append((r + g + b) / 3)
            }
        }

        brightnessValues.sort()
        var inliers = 0
        if count > 0 {
            let median = brightnessValues[count / 2]
            for val in brightnessValues {
                if abs(val - median) <= 35 {
                    inliers += 1
                }
            }
        }
        return count >= 29 && leftHits >= 6 && rightHits >= 6 && topHits >= 5 && bottomHits >= 5
            && (blue || Double(inliers) >= Double(count) * 0.80)
    }

    private static func hasUpperBoundary(panel: [Int], above: [Int]) -> Bool {
        var rs = [Int]()
        var gs = [Int]()
        var bs = [Int]()
        rs.reserveCapacity(panel.count)
        gs.reserveCapacity(panel.count)
        bs.reserveCapacity(panel.count)

        for pixel in panel {
            if pixel < 0 { continue }
            rs.append((pixel >> 16) & 255)
            gs.append((pixel >> 8) & 255)
            bs.append(pixel & 255)
        }
        let count = rs.count
        if count < 29 { return false }
        rs.sort()
        gs.sort()
        bs.sort()
        let medR = rs[count / 2]
        let medG = gs[count / 2]
        let medB = bs[count / 2]
        let bluePanel = medB > 70 && medB > medR + 24 && medB > medG + 8
        let minimumContrast = bluePanel ? 50 : 100
        var different = 0
        for pixel in above {
            if pixel < 0 { continue }
            let dist = abs(((pixel >> 16) & 255) - medR)
                + abs(((pixel >> 8) & 255) - medG)
                + abs((pixel & 255) - medB)
            if dist >= minimumContrast { different += 1 }
        }
        return different >= 5
    }

    private static func hasCompactOutline(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        buffer: CropImageBuffer
    ) -> Bool {
        let fullW = buffer.fullWidth
        let fullH = buffer.fullHeight
        let width = right - left
        let height = bottom - top
        if height < 4 || Double(width) < Double(fullW) * 0.10 || Double(width) > Double(fullW) * 0.48
            || width < 7 * height || left < 0 || right > fullW {
            return false
        }
        let upper = findStroke(left: left, width: width, start: top, height: height, direction: -1, fullH: fullH, buffer: buffer)
        if upper < 0 { return false }
        let lower = findStroke(left: left, width: width, start: bottom, height: height, direction: 1, fullH: fullH, buffer: buffer)
        return lower >= 0 && lower - upper >= 2 * height && lower - upper <= 5 * height
    }

    private static func findStroke(
        left: Int,
        width: Int,
        start: Int,
        height: Int,
        direction: Int,
        fullH: Int,
        buffer: CropImageBuffer
    ) -> Int {
        let spacing = max(2, height / 4)
        for distance in spacing...(4 * height) {
            let y = start + distance * direction
            if y < spacing || y >= fullH - spacing { break }
            var votes = 0
            for column in 1...7 {
                let x = left + width * column / 8
                let center = buffer.luma(fullX: x, fullY: y)
                let up = buffer.luma(fullX: x, fullY: y - spacing)
                let down = buffer.luma(fullX: x, fullY: y + spacing)
                if center >= 0 && up >= 0 && down >= 0 && (min(up, down) - center) >= 14 {
                    votes += 1
                }
            }
            if votes >= 5 { return y }
        }
        return -1
    }
}

func extractFrameIndex(filename: String, fallback: Int) -> Int {
    let digits = filename.filter { $0.isNumber }
    if let val = Int(digits), val > 0 {
        return val
    }
    return fallback
}

struct TsvWord {
    let left: Int
    let top: Int
    let width: Int
    let height: Int
    let conf: Double
    let text: String
}

enum TesseractError: Error, CustomStringConvertible {
    case exitFailure(Int32)
    case imageLoadFailed

    var description: String {
        switch self {
        case .exitFailure(let code): return "tesseract Exit-Code \(code)"
        case .imageLoadFailed: return "Bild konnte nicht geladen werden"
        }
    }
}

func runTesseractOCR(
    url: URL,
    cropTopPx: Int,
    fullWidth: Int,
    fullHeight: Int
) throws -> [OcrLineOutput] {
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
    process.arguments = ["tesseract", url.path, "stdout", "-l", "deu", "--psm", "3", "tsv"]
    var env = ProcessInfo.processInfo.environment
    env["OMP_THREAD_LIMIT"] = "1"
    process.environment = env

    let outPipe = Pipe()
    process.standardOutput = outPipe
    process.standardError = FileHandle.nullDevice

    try process.run()
    let data = outPipe.fileHandleForReading.readDataToEndOfFile()
    process.waitUntilExit()

    guard process.terminationStatus == 0 else {
        throw TesseractError.exitFailure(process.terminationStatus)
    }

    let tsv = String(data: data, encoding: .utf8) ?? ""
    var order = [String]()
    var groups = [String: [TsvWord]]()

    for rawLine in tsv.split(separator: "\n", omittingEmptySubsequences: true).dropFirst() {
        let trimmedLine = rawLine.last == "\r" ? rawLine.dropLast() : rawLine
        let cols = trimmedLine.split(separator: "\t", omittingEmptySubsequences: false)
        guard cols.count >= 12, cols[0] == "5" else { continue }
        let wordText = String(cols[11]).trimmingCharacters(in: .whitespacesAndNewlines)
        if wordText.isEmpty { continue }
        guard let conf = Double(cols[10]), conf >= 20.0 else { continue }
        guard let wLeft = Int(cols[6]), let wTop = Int(cols[7]),
              let wWidth = Int(cols[8]), let wHeight = Int(cols[9]),
              wWidth > 0, wHeight > 0 else {
            continue
        }
        let key = "\(cols[2]):\(cols[3]):\(cols[4])"
        if groups[key] == nil {
            order.append(key)
            groups[key] = []
        }
        groups[key]?.append(TsvWord(
            left: wLeft,
            top: wTop,
            width: wWidth,
            height: wHeight,
            conf: conf,
            text: wordText
        ))
    }

    if order.isEmpty {
        return []
    }

    guard let buffer = CropImageBuffer(
        url: url,
        cropTopPx: cropTopPx,
        fullWidth: fullWidth,
        fullHeight: fullHeight
    ) else {
        throw TesseractError.imageLoadFailed
    }

    let scaleX = buffer.width == fullWidth ? 1.0 : Double(fullWidth) / Double(max(1, buffer.width))
    let expectedCropH = max(1, fullHeight - cropTopPx)
    let scaleY = buffer.height == expectedCropH ? 1.0 : Double(expectedCropH) / Double(max(1, buffer.height))

    var lines = [OcrLineOutput]()
    lines.reserveCapacity(order.count)

    for key in order {
        guard let words = groups[key], !words.isEmpty else { continue }
        let rawLeft = words.map(\.left).min() ?? 0
        let rawTop = words.map(\.top).min() ?? 0
        let rawRight = words.map { $0.left + $0.width }.max() ?? 0
        let rawBottom = words.map { $0.top + $0.height }.max() ?? 0

        let left = max(0, min(fullWidth - 1, Int((Double(rawLeft) * scaleX).rounded())))
        let right = max(left + 1, min(fullWidth, Int((Double(rawRight) * scaleX).rounded())))
        let top = max(cropTopPx, min(fullHeight - 1, cropTopPx + Int((Double(rawTop) * scaleY).rounded())))
        let bottom = max(top + 1, min(fullHeight, cropTopPx + Int((Double(rawBottom) * scaleY).rounded())))

        let joined = words.map(\.text).joined(separator: " ")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if joined.isEmpty { continue }

        let avgConf = words.reduce(0.0) { $0 + $1.conf } / Double(words.count) / 100.0
        let cx = Double(left + right) / 2.0 / Double(fullWidth)
        let cy = Double(top + bottom) / 2.0 / Double(fullHeight)

        let surface: BubbleSurface
        if BubbleClassifier.inDialogueRegion(centerX: cx, centerY: cy) {
            surface = BubbleClassifier.classifyDialogue(
                buffer: buffer,
                left: left,
                top: top,
                right: right,
                bottom: bottom
            )
        } else {
            surface = .none
        }

        lines.append(OcrLineOutput(
            text: joined,
            left: left,
            top: top,
            right: right,
            bottom: bottom,
            confidence: (avgConf * 10000.0).rounded() / 10000.0,
            surface: surface.rawValue
        ))
    }

    return lines
}

enum FrameEvent {
    case boundary(frameIndex: Int, timestampSec: Double)
    case candidate(url: URL, frameIndex: Int, timestampSec: Double)
}

final class OcrResultStore: @unchecked Sendable {
    private let lock = NSLock()
    private var results = [Int: [OcrLineOutput]]()
    private(set) var ocrFrames = 0
    private(set) var ocrErrors = 0

    func recordSuccess(index: Int, lines: [OcrLineOutput]) {
        lock.lock()
        results[index] = lines
        ocrFrames += 1
        lock.unlock()
    }

    func recordError(frameIndex: Int, error: Error) {
        lock.lock()
        ocrErrors += 1
        lock.unlock()
        fputs("OCR-Fehler bei Frame \(frameIndex): \(error)\n", stderr)
    }

    func linesFor(index: Int) -> [OcrLineOutput]? {
        lock.lock()
        defer { lock.unlock() }
        return results[index]
    }
}

func main() {
    let config = parseArgs()
    let startTime = Date()
    let fm = FileManager.default
    let dirURL = URL(fileURLWithPath: config.framesDir, isDirectory: true)
    let files: [URL]
    do {
        files = try fm.contentsOfDirectory(at: dirURL, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension.lowercased() == "png" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
    } catch {
        fputs("Fehler beim Lesen von \(config.framesDir): \(error)\n", stderr)
        exit(1)
    }

    let outURL = URL(fileURLWithPath: config.outPath)
    try? fm.createDirectory(at: outURL.deletingLastPathComponent(), withIntermediateDirectories: true)
    fm.createFile(atPath: config.outPath, contents: nil)
    guard let handle = try? FileHandle(forWritingTo: outURL) else {
        fputs("Kann Ausgabedatei nicht öffnen: \(config.outPath)\n", stderr)
        exit(1)
    }
    defer { try? handle.close() }

    let cropTopPx = Int((config.cropYRatio * Double(config.fullHeight)).rounded())

    // Pass 1: Serieller Settle-then-OCR + Change-/Color-Filter ohne OCR-State
    var totalFrames = 0
    var skippedUnchanged = 0
    var skippedChanging = 0
    var skippedNoBubble = 0

    struct PendingRef {
        let url: URL
        let frameIndex: Int
        let timestampSec: Double
    }

    var events = [FrameEvent]()
    var pending: PendingRef? = nil
    var prevBubbleFP: [UInt8]? = nil
    var stableCount = 0
    var ocrDoneForRun = false
    var inBubble = false

    for (idx, fileURL) in files.enumerated() {
        autoreleasepool {
            totalFrames += 1
            guard let buffer = CropImageBuffer(
                url: fileURL,
                cropTopPx: cropTopPx,
                fullWidth: config.fullWidth,
                fullHeight: config.fullHeight
            ) else {
                return
            }

            let frameNumber = extractFrameIndex(filename: fileURL.lastPathComponent, fallback: idx + 1)
            let timestampSec = Double(frameNumber - 1) / config.fps

            if !buffer.hasBubbleCandidateColor() {
                if let p = pending, !ocrDoneForRun {
                    events.append(.candidate(url: p.url, frameIndex: p.frameIndex, timestampSec: p.timestampSec))
                }
                if inBubble {
                    events.append(.boundary(frameIndex: frameNumber, timestampSec: timestampSec))
                    inBubble = false
                }
                pending = nil
                prevBubbleFP = nil
                stableCount = 0
                ocrDoneForRun = false
                skippedNoBubble += 1
                return
            }

            inBubble = true
            let fp = buffer.dialogueFingerprint()
            let current = PendingRef(url: fileURL, frameIndex: frameNumber, timestampSec: timestampSec)

            guard let prev = prevBubbleFP else {
                pending = current
                prevBubbleFP = fp
                stableCount = 0
                ocrDoneForRun = false
                return
            }

            let diff = framesDiffer(fp, prev)
            if !diff.differ {
                stableCount += 1
                pending = current
                if !ocrDoneForRun && stableCount >= 1 {
                    events.append(.candidate(url: fileURL, frameIndex: frameNumber, timestampSec: timestampSec))
                    ocrDoneForRun = true
                } else {
                    skippedUnchanged += 1
                }
            } else {
                if !ocrDoneForRun, let p = pending, diff.overallMAD >= 10.0 {
                    events.append(.candidate(url: p.url, frameIndex: p.frameIndex, timestampSec: p.timestampSec))
                } else {
                    skippedChanging += 1
                }
                pending = current
                prevBubbleFP = fp
                stableCount = 0
                ocrDoneForRun = false
            }
        }
    }

    if !ocrDoneForRun, let p = pending {
        events.append(.candidate(url: p.url, frameIndex: p.frameIndex, timestampSec: p.timestampSec))
    }

    // Pass 2: Parallele Tesseract-Ausführung über gesettelte Kandidaten
    var candidateJobs = [(eventIndex: Int, url: URL, frameIndex: Int)]()
    for (idx, ev) in events.enumerated() {
        if case let .candidate(url, frameIndex, _) = ev {
            candidateJobs.append((idx, url, frameIndex))
        }
    }

    let store = OcrResultStore()
    let semaphore = DispatchSemaphore(value: config.workers)
    let jobsSnapshot = candidateJobs
    let fullW = config.fullWidth
    let fullH = config.fullHeight

    DispatchQueue.concurrentPerform(iterations: jobsSnapshot.count) { jobIdx in
        semaphore.wait()
        defer { semaphore.signal() }
        let job = jobsSnapshot[jobIdx]
        autoreleasepool {
            do {
                let lines = try runTesseractOCR(
                    url: job.url,
                    cropTopPx: cropTopPx,
                    fullWidth: fullW,
                    fullHeight: fullH
                )
                store.recordSuccess(index: job.eventIndex, lines: lines)
            } catch {
                store.recordError(frameIndex: job.frameIndex, error: error)
            }
        }
    }

    // Pass 3: Serielles Schreiben in zeitlicher Reihenfolge
    let encoder = JSONEncoder()
    encoder.outputFormatting = []

    func writeRecord(frameIndex: Int, timestampSec: Double, lines: [OcrLineOutput]) {
        let record = FrameOutput(
            frame_index: frameIndex,
            timestamp_sec: (timestampSec * 1000.0).rounded() / 1000.0,
            width: config.fullWidth,
            height: config.fullHeight,
            lines: lines
        )
        if var data = try? encoder.encode(record) {
            data.append(0x0A)
            handle.write(data)
        }
    }

    for (idx, ev) in events.enumerated() {
        switch ev {
        case let .boundary(frameIndex, timestampSec):
            writeRecord(frameIndex: frameIndex, timestampSec: timestampSec, lines: [])
        case let .candidate(_, frameIndex, timestampSec):
            if let lines = store.linesFor(index: idx) {
                writeRecord(frameIndex: frameIndex, timestampSec: timestampSec, lines: lines)
            }
        }
    }

    let elapsed = Date().timeIntervalSince(startTime)
    let stats = StatsOutput(
        total_frames: totalFrames,
        skipped_unchanged: skippedUnchanged,
        skipped_changing: skippedChanging,
        skipped_no_bubble: skippedNoBubble,
        ocr_frames: store.ocrFrames,
        ocr_errors: store.ocrErrors,
        elapsed_sec: (elapsed * 100.0).rounded() / 100.0
    )
    if var statsData = try? encoder.encode(stats) {
        statsData.append(0x0A)
        FileHandle.standardError.write(statsData)
    }
}

main()
