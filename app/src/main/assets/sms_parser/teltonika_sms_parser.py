#!/usr/bin/env python3
"""
Parser for Teltonika FM2200 24-hour Position SMS Data Protocol
Decodes compressed GPS data from SMS messages
"""

class BitStream:
    """Helper class to read bits from a byte stream"""
    def __init__(self, data):
        self.data = data
        self.bit_pos = 0

    def read_bits(self, num_bits):
        """Read specified number of bits and return as integer"""
        result = 0
        for i in range(num_bits):
            byte_idx = self.bit_pos // 8
            bit_idx = self.bit_pos % 8
            bit = (self.data[byte_idx] >> bit_idx) & 1
            result |= (bit << i)
            self.bit_pos += 1
        return result

    def align_to_byte(self):
        """Align to next byte boundary"""
        if self.bit_pos % 8 != 0:
            self.bit_pos += 8 - (self.bit_pos % 8)


def parse_teltonika_sms(hex_string):
    """
    Parse Teltonika FM2200 24-hour position SMS

    Args:
        hex_string: Hex-encoded SMS data

    Returns:
        Dictionary with parsed data
    """
    # Convert hex string to bytes
    data = bytes.fromhex(hex_string)
    stream = BitStream(data)

    result = {
        'raw_hex': hex_string,
        'raw_bytes': data.hex(),
        'entries': []
    }

    # Parse CodecId (8 bits)
    codec_id = stream.read_bits(8)
    result['codec_id'] = codec_id

    if codec_id != 4:
        result['error'] = f'Invalid CodecId: {codec_id}. Expected 4 for 24-hour SMS.'
        return result

    # Parse Timestamp (35 bits)
    # Time in seconds elapsed from 2000.01.01 00:00 EET
    timestamp_seconds = stream.read_bits(35)
    result['timestamp_seconds'] = timestamp_seconds

    # Convert to readable date
    from datetime import datetime, timedelta
    base_time = datetime(2000, 1, 1, 0, 0, 0)
    element_time = base_time + timedelta(seconds=timestamp_seconds)
    result['timestamp'] = element_time.isoformat()
    result['timestamp_utc'] = element_time.strftime('%Y-%m-%d %H:%M:%S UTC+2')

    # Parse ElementCount (5 bits)
    element_count = stream.read_bits(5)
    result['element_count'] = element_count

    # Parse GPS Data Elements
    prev_longitude = 0
    prev_latitude = 0

    for i in range(element_count):
        element = {}
        element['index'] = i
        element['time_offset_hours'] = i  # Each element is 1 hour apart

        # ValidElement (1 bit)
        valid = stream.read_bits(1)
        element['valid'] = bool(valid)

        if not valid:
            element['data'] = 'Invalid/Empty'
            result['entries'].append(element)
            continue

        # DifferentialCoords (1 bit)
        differential = stream.read_bits(1)
        element['differential_coords'] = bool(differential)

        if differential:
            # Read differential coordinates (14 bits each)
            lon_diff = stream.read_bits(14)
            lat_diff = stream.read_bits(14)

            element['longitude_diff'] = lon_diff
            element['latitude_diff'] = lat_diff

            # Decode: Longitude = prevLongitude - LongitudeDiff + 2^13 - 1
            #         Latitude = prevLatitude - LatitudeDiff + 2^13 - 1
            OFFSET = (2 ** 13) - 1
            longitude = prev_longitude - lon_diff + OFFSET
            latitude = prev_latitude - lat_diff + OFFSET

            element['longitude_raw'] = longitude
            element['latitude_raw'] = latitude
        else:
            # Read absolute coordinates
            longitude = stream.read_bits(21)
            latitude = stream.read_bits(20)

            element['longitude_raw'] = longitude
            element['latitude_raw'] = latitude

        # Read Speed (8 bits)
        speed = stream.read_bits(8)
        element['speed_kmh'] = speed

        # Convert raw coordinates to degrees
        # LongDeg = Longitude * 360 / (2^21 - 1) - 180
        # LatDeg = Latitude * 180 / (2^20 - 1) - 90
        lon_deg = (longitude * 360.0) / (2**21 - 1) - 180.0
        lat_deg = (latitude * 180.0) / (2**20 - 1) - 90.0

        element['longitude_deg'] = round(lon_deg, 8)
        element['latitude_deg'] = round(lat_deg, 8)

        # Calculate actual timestamp for this element
        element_time_obj = element_time + timedelta(hours=i)
        element['timestamp'] = element_time_obj.isoformat()

        result['entries'].append(element)

        # Update previous coordinates for next iteration
        prev_longitude = longitude
        prev_latitude = latitude

    # Align to byte boundary for IMEI
    stream.align_to_byte()

    # Parse IMEI (64 bits = 8 bytes)
    # IMEI is stored as big-endian 64-bit integer
    byte_idx = stream.bit_pos // 8
    imei_bytes = data[byte_idx:byte_idx+8]
    imei = int.from_bytes(imei_bytes, byteorder='big')
    result['imei'] = str(imei)
    result['imei_hex'] = f'{imei:016x}'

    result['success'] = True
    return result


def print_results(parsed_data):
    """Pretty print parsed results"""
    if 'error' in parsed_data:
        print(f"❌ Error: {parsed_data['error']}")
        return

    print(f"✓ Teltonika FM2200 24-Hour Position SMS Parser")
    print(f"{'='*70}")
    print(f"CodecId: {parsed_data['codec_id']}")
    print(f"Timestamp: {parsed_data['timestamp_utc']}")
    print(f"Element Count: {parsed_data['element_count']}")
    print(f"IMEI: {parsed_data['imei']}")
    print(f"{'='*70}\n")

    print(f"GPS Data Entries:")
    print(f"{'-'*70}")

    for entry in parsed_data['entries']:
        print(f"\nEntry #{entry['index']}")
        if not entry['valid']:
            print(f"  Status: Invalid/Empty")
        else:
            print(f"  Time: {entry['timestamp']} (+{entry['time_offset_hours']}h from base)")
            print(f"  Location: {entry['latitude_deg']:.8f}°N, {entry['longitude_deg']:.8f}°E")
            print(f"  Speed: {entry['speed_kmh']} km/h")
            if entry['differential_coords']:
                print(f"  Encoding: Differential")
            else:
                print(f"  Encoding: Absolute")


if __name__ == '__main__':
    # Test with provided data
    sms_hex = "04C0743932C00000804CEC6B018E0200000140E9D530E7A8"

    print(f"Parsing SMS: {sms_hex}\n")
    parsed = parse_teltonika_sms(sms_hex)
    print_results(parsed)

    # Also print detailed JSON-like output
    print(f"\n{'='*70}")
    print(f"Detailed Raw Data:")
    print(f"{'='*70}")
    import json
    # Convert to JSON-serializable format
    output = {
        'codec_id': parsed['codec_id'],
        'timestamp': parsed['timestamp'],
        'element_count': parsed['element_count'],
        'imei': parsed['imei'],
        'entries': [
            {
                'index': e['index'],
                'valid': e['valid'],
                'timestamp': e.get('timestamp', 'N/A'),
                'latitude_deg': e.get('latitude_deg', None),
                'longitude_deg': e.get('longitude_deg', None),
                'speed_kmh': e.get('speed_kmh', None),
                'differential': e.get('differential_coords', None)
            }
            for e in parsed['entries']
        ]
    }
    print(json.dumps(output, indent=2))
