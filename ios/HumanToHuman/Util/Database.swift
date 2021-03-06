// SQLite Database utilities.

import Foundation

// Property IDs. These are used in the database metadata table to store data.
let KEY_OWN_ID = 0
let KEY_CURRENT_CURSOR = 1
let KEY_PRIVACY_POLICY = 3
let KEY_EXPERIMENT_DESCRIPTION = 6
let KEY_SERVER_BASE_URL = 7
let KEY_APP_STATE = 8
let KEY_TOKEN = 9
let KEY_NOISE = 10

// Default values for certain database metadata keys
let VALUE_DEFAULT_EXPERIMENT_DESCRIPTION = "EXPERIMENT DESCRIPTION:\nHELLO WORLD!"
let VALUE_DEFAULT_PRIVACY_POLICY = "PRIVACY POLICY:\nHELLO WORLD!"

// Shared database
let shared: FMDatabase = {
    let fileURL = try! FileManager.default
        .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        .appendingPathComponent("database.sqlite")
    let database = FMDatabase(url: fileURL)
    return database
}()
let mutex = DispatchSemaphore(value: 1)

// A row in the sensor_data table.
struct Row {
    let id: UInt64
    let time: Date
    let source: UInt64
    let power: Int
    let rssi: Double
}

struct Database {
    // Initializes the local database.
    static func initDatabase() -> Bool {
        guard shared.open() else {
            print("failed to open database")
            return false
        }

        guard shared.executeStatements(
            """
            CREATE TABLE IF NOT EXISTS metadata (
                key_        INTEGER         PRIMARY KEY,
                tvalue      TEXT            NOT NULL DEFAULT '',
                nvalue      INTEGER         NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS sensor_data (
                id          INTEGER         PRIMARY KEY AUTOINCREMENT,
                time        INTEGER         NOT NULL,
                source      INTEGER         NOT NULL,
                power       INTEGER         NOT NULL,
                rssi        REAL            NOT NULL
            );
            """) else {
            print(shared.lastErrorMessage())
            return false
        }
        return true
    }

    static func clearProp(prop: Int) {
        mutex.wait()
        try? shared.executeUpdate("DELETE FROM metadata where key_ = ?", values: [prop])
        mutex.signal()
    }

    static func getPropText(prop: Int) -> String? {
        mutex.wait()
        let rs = shared.executeQuery("SELECT tvalue from metadata WHERE key_ = ?",
                                     withArgumentsIn: [prop])

        if let rs = rs, rs.next() {
            let val = rs.string(forColumn: "tvalue")
            rs.close()
            mutex.signal()
            return val
        } else {
            mutex.signal()
            return nil
        }
    }

    static func setPropText(prop: Int, value: String) {
        mutex.wait()
        try? shared.executeUpdate("INSERT OR REPLACE INTO metadata (key_, tvalue) VALUES (?, ?)",
                                     values: [prop, value])
        mutex.signal()
    }

    // Gets a numeric property from the metadata table.
    static func getPropNumeric(prop: Int) -> UInt64? {
        mutex.wait()
        let rs = shared.executeQuery("SELECT nvalue from metadata WHERE key_ = ?",
                                     withArgumentsIn: [prop])
        if let rs = rs, rs.next() {
            let val =  UInt64(bitPattern: rs.longLongInt(forColumn: "nvalue"))
            rs.close()
            mutex.signal()
            return val
        } else {
            mutex.signal()
            return nil
        }
    }

    // Sets a numeric property in the metadata table.
    static func setPropNumeric(prop: Int, value: Int64) {
        mutex.wait()
        try? shared.executeUpdate("INSERT OR REPLACE INTO metadata (key_, nvalue) VALUES (?, ?)",
                                     values: [prop, value])
        mutex.signal()
    }

    // Pops all rows from the sensor_data table, reading then deleting them.
    static func popRows() -> [Row] {
        do {
            mutex.wait()
            var rs = try shared.executeQuery("SELECT MAX(id) as max_id FROM sensor_data LIMIT 1", values: nil)
            rs.next()
            let rowMax = rs.longLongInt(forColumn: "max_id")
            rs.close()

            rs = try shared.executeQuery("SELECT * FROM sensor_data WHERE id <= ?", values: [rowMax])
            var list: [Row] = []
            while rs.next() {
                let timeNumber = Double(rs.longLongInt(forColumn: "time")) / 1000.0
                let row = Row(
                    id: UInt64(bitPattern: rs.longLongInt(forColumn: "id")),
                    time: Date(timeIntervalSince1970: timeNumber),
                    source: UInt64(bitPattern: rs.longLongInt(forColumn: "source")),
                    power: rs.long(forColumn: "power"),
                    rssi: rs.double(forColumn: "rssi")
                )
                list.append(row)
            }
            rs.close()
            shared.executeUpdate("DELETE FROM sensor_data WHERE id <= ?", withArgumentsIn: [rowMax])
            mutex.signal()
            return list
        } catch {
            print(shared.lastErrorMessage())
            mutex.signal()
            return []
        }
    }

    // Write a row to the database.
    static func writeRow(device: Device) -> Bool {
        mutex.wait()
        let val = shared.executeUpdate(
            """
            insert into sensor_data (time, source, power, rssi)
            values (strftime('%s','now') || substr(strftime('%f','now'),4), ?, ?, ?)
            """,
            withArgumentsIn: [device.uuid, device.measuredPower, device.rssi]
        )
        mutex.signal()
        return val
    }

    // Read all rows from the database.
    static func rowCount() -> Int {
        do {
            mutex.wait()
            // Get a resultset from the database cooresponding to all rows in the sensor_data table
            let rs = try shared.executeQuery("SELECT COUNT(id) AS row_count FROM sensor_data", values: nil)
            guard rs.next() else {
                return 0
            }

            let rowCount = rs.long(forColumn: "row_count")
            rs.close()
            mutex.signal()
            return rowCount
        } catch {
            print(shared.lastErrorMessage())
            mutex.signal()
            return 0
        }
    }
}
