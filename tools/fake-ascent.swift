// A fake ASCENT board on this Mac, for checking the app's connected card without a real board.
//
// It advertises as "ASCENT-MAC", offers characteristic 0x0001 (the one the app reads and writes),
// prints every write in hex with what each kind of firmware would do with it, and answers reads
// like one of the two kinds of ASCENT firmware:
//   telemetry  packet firmware (irec-dev, fsm-unique-counters-dev, ...): a GOOBER packet with class 21
//              (TELEMETRY) and fixed readings
//   battery    first-byte firmware (flight, dev, ...): one byte, 25 x the battery's whole volts
//
// Build and run from the repo root:
//   mkdir -p build && swiftc -O tools/fake-ascent.swift -o build/fake-ascent
//   build/fake-ascent telemetry 900     (what reads answer with, then how many seconds to run)
//
// The app only lists boards whose name contains "ASCENT". If the iPhone shows this Mac under the Mac's
// own name instead, rename the Mac to "ASCENT-MAC" (System Settings > General > About) for the test.
import CoreBluetooth
import Foundation

setbuf(stdout, nil)

// The firmware's message classes the app can send (main/command/command.h in ascent-r3-firmware)
let commandNames: [UInt8: String] = [
    4: "REBOOT", 5: "PYRO1 (Pop Apogee)", 6: "PYRO2 (Pop Mains)", 9: "AUX_ON", 10: "AUX_OFF",
    12: "WAKE_UP", 13: "TXLOCK_ON", 22: "ERASE_FLASH",
]

func hexString(_ data: Data) -> String { data.map { String(format: "0x%02X", $0) }.joined(separator: " ") }

// What each kind of firmware would do with a write
func meaning(_ data: Data) -> String {
    let bytes = [UInt8](data)
    guard let first = bytes.first else { return "empty write" }
    // First-byte firmware runs the first byte
    let firstByte = commandNames[first] ?? "rejected (\(first))"
    // Packet firmware decodes a GOOBER packet, whose length must match its header, and runs MSG_CLS
    let packet = bytes.count >= 5 && bytes.count == 5 + Int(bytes[4])
        ? commandNames[bytes[3]] ?? "rejected (\(bytes[3]))"
        : "rejected (not a GOOBER packet)"
    return "first-byte firmware: \(firstByte) | packet firmware: \(packet)"
}

enum ReadReply: String { case telemetry, battery }

final class FakeBoard: NSObject, CBPeripheralManagerDelegate {
    private var manager: CBPeripheralManager!
    private let name = "ASCENT-MAC"
    private let reply: ReadReply
    // Any service works: the app looks for characteristic 0x0001 in every service
    private let serviceUUID = CBUUID(string: "0000FFE0-0000-1000-8000-00805F9B34FB")
    private let characteristicUUID = CBUUID(string: "00000001-0000-1000-8000-00805F9B34FB")
    private let start = Date()
    private var seq: UInt8 = 0
    private var reads = 0

    init(reply: ReadReply) {
        self.reply = reply
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print("Bluetooth state: \(peripheral.state.rawValue) (5 = on, 3 = not allowed)")
        guard peripheral.state == .poweredOn else { return }
        let characteristic = CBMutableCharacteristic(
            type: characteristicUUID, properties: [.read, .write], value: nil, permissions: [.readable, .writeable]
        )
        let service = CBMutableService(type: serviceUUID, primary: true)
        service.characteristics = [characteristic]
        peripheral.add(service)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error { print("adding the service failed: \(error)"); return }
        // The name only: a service UUID in the advertisement pushes the name out
        peripheral.startAdvertising([CBAdvertisementDataLocalNameKey: name])
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error { print("advertising failed: \(error)") }
        else { print("advertising as \(name); reads answer with \(reply.rawValue)") }
    }

    // DEV_ID 130, DEV_MODE 0x0D, SEQ_ID, MSG_CLS 21 (TELEMETRY), payload length 34, then the payload
    private func telemetryPacket() -> Data {
        seq &+= 1
        var d = Data([130, 0x0D, seq, 21, 34])
        func put<T: FixedWidthInteger>(_ v: T) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        func putF(_ v: Float) { put(v.bitPattern) }
        put(UInt32(Date().timeIntervalSince(start) * 1000))   // timestamp, ms
        put(Int32(334_215_000))                                // latitude
        put(Int32(-1_119_340_000))                             // longitude
        putF(1.5)                                              // altitude AGL, m
        putF(0)                                                // vertical velocity, m/s
        putF(9.75)                                             // Y acceleration
        putF(0.25)                                             // Y gyro
        d.append(0b01)                                         // pyro state: continuity on channel 1 only
        d.append(9)                                            // satellites
        d.append(0)                                            // flight state
        d.append(0)                                            // padding
        put(UInt16(20_625))                                    // battery, 8.25 V
        return d
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        // Battery mode: one byte, 25 x 8 whole volts
        let value = reply == .telemetry ? telemetryPacket() : Data([200])
        guard request.offset <= value.count else {
            peripheral.respond(to: request, withResult: .invalidOffset)
            return
        }
        request.value = value.subdata(in: request.offset..<value.count)
        peripheral.respond(to: request, withResult: .success)
        reads += 1
        if reads == 1 || reads % 500 == 0 { print("served \(reads) reads") }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        let stamp = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
        for request in requests {
            let data = request.value ?? Data()
            print("[\(stamp)] WRITE \(hexString(data)) -> \(meaning(data))")
        }
        peripheral.respond(to: requests[0], withResult: .success)
    }
}

let arguments = CommandLine.arguments
let reply = arguments.count > 1 ? ReadReply(rawValue: arguments[1]) ?? .telemetry : .telemetry
let seconds = arguments.count > 2 ? Double(arguments[2]) ?? 900 : 900
let board = FakeBoard(reply: reply)
RunLoop.main.run(until: Date().addingTimeInterval(seconds))
print("stopped")
