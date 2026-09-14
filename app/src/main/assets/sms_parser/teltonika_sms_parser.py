#!/usr/bin/env python3
"""
Enhanced Teltonika FM2200 24-hour Position SMS Parser
Handles out-of-order delivery, deduplication, and validation
"""

from datetime import datetime, timedelta, timezone
import json
import math
import struct

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

    if codec_id == 8:
        return parse_codec8_sms(data, result)

    if codec_id != 4:
        result['error'] = f'Invalid CodecId: {codec_id}. Expected 4 for 24-hour SMS.'
        return result

    # Parse Timestamp (35 bits)
    timestamp_seconds = stream.read_bits(35)
    result['timestamp_seconds'] = timestamp_seconds

    # Convert to readable date (2000-01-01 EET)
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
        element['time_offset_hours'] = i

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
            lon_diff = stream.read_bits(14)
            lat_diff = stream.read_bits(14)

            element['longitude_diff'] = lon_diff
            element['latitude_diff'] = lat_diff

            OFFSET = (2 ** 13) - 1
            longitude = prev_longitude - lon_diff + OFFSET
            latitude = prev_latitude - lat_diff + OFFSET

            element['longitude_raw'] = longitude
            element['latitude_raw'] = latitude
        else:
            longitude = stream.read_bits(21)
            latitude = stream.read_bits(20)

            element['longitude_raw'] = longitude
            element['latitude_raw'] = latitude

        # Read Speed (8 bits)
        speed = stream.read_bits(8)
        element['speed_kmh'] = speed

        # Convert raw coordinates to degrees
        lon_deg = (longitude * 360.0) / (2**21 - 1) - 180.0
        lat_deg = (latitude * 180.0) / (2**20 - 1) - 90.0

        element['longitude_deg'] = round(lon_deg, 8)
        element['latitude_deg'] = round(lat_deg, 8)

        # Calculate actual timestamp for this element
        element_time_obj = element_time - timedelta(hours=(element_count - 1 - i))
        element['timestamp'] = element_time_obj.isoformat()
        element['timestamp_datetime'] = element_time_obj

        result['entries'].append(element)

        # Update previous coordinates for next iteration
        prev_longitude = longitude
        prev_latitude = latitude

    # Parse IMEI (64 bits = 8 bytes)
    stream.align_to_byte()
    byte_idx = stream.bit_pos // 8
    imei_bytes = data[byte_idx:byte_idx+8]
    imei = int.from_bytes(imei_bytes, byteorder='big')
    result['imei'] = str(imei)
    result['imei_hex'] = f'{imei:016x}'

    result['success'] = True
    return result


def parse_codec8_sms(data, result):
    if len(data) < 2:
        result['error'] = 'Data too short for Codec 8 header'
        return result

    idx = 1
    element_count = data[idx]
    idx += 1
    result['element_count'] = element_count

    for i in range(element_count):
        if idx + 24 > len(data):
            result['error'] = f'Truncated Codec 8 record data at index {i}'
            return result

        ts_ms = struct.unpack('>Q', data[idx:idx+8])[0]
        priority = data[idx+8]
        lon_int = struct.unpack('>i', data[idx+9:idx+13])[0]
        lat_int = struct.unpack('>i', data[idx+13:idx+17])[0]
        alt = struct.unpack('>h', data[idx+17:idx+19])[0]
        angle = struct.unpack('>H', data[idx+19:idx+21])[0]
        sats = data[idx+21]
        speed = struct.unpack('>H', data[idx+22:idx+24])[0]
        idx += 24

        lon_deg = lon_int / 10000000.0
        lat_deg = lat_int / 10000000.0

        if idx + 2 > len(data):
            result['error'] = 'Truncated Codec 8 IO element header'
            return result

        event_io_id = data[idx]
        total_io = data[idx+1]
        idx += 2

        n1 = data[idx]
        idx += 1 + n1 * 2

        n2 = data[idx]
        idx += 1 + n2 * 3

        n4 = data[idx]
        idx += 1 + n4 * 5

        n8 = data[idx]
        idx += 1 + n8 * 9

        elem_time = datetime.fromtimestamp(ts_ms / 1000.0, timezone.utc)

        entry = {
            'index': i,
            'valid': True,
            'timestamp': elem_time.isoformat(),
            'timestamp_datetime': elem_time,
            'latitude_deg': round(lat_deg, 8),
            'longitude_deg': round(lon_deg, 8),
            'speed_kmh': speed,
            'differential_coords': False,
            'time_offset_hours': 0
        }
        result['entries'].append(entry)

    if idx < len(data):
        idx += 1  # element count 2

    if idx + 8 <= len(data):
        imei_bytes = data[idx:idx+8]
        imei = int.from_bytes(imei_bytes, byteorder='big')
        result['imei'] = str(imei)
        result['imei_hex'] = f'{imei:016x}'

    if result['entries']:
        result['timestamp'] = result['entries'][-1]['timestamp']
        result['timestamp_utc'] = result['entries'][-1]['timestamp'] + ' UTC'

    result['success'] = True
    return result


def filter_and_sort_positions(parsed_data):
    """
    Filter invalid entries and sort by timestamp

    Returns list of valid entries sorted chronologically
    """
    valid_entries = [e for e in parsed_data['entries'] if e.get('valid')]
    valid_entries.sort(key=lambda x: x['timestamp_datetime'])
    return valid_entries


def deduplicate_positions(position_list, imei):
    """
    Remove duplicate positions based on (IMEI, timestamp) pairs
    """
    seen = set()
    unique_positions = []

    for pos in position_list:
        position_key = (imei, pos['timestamp'])
        if position_key not in seen:
            seen.add(position_key)
            unique_positions.append(pos)
        else:
            print(f"DEBUG: Skipping duplicate at {pos['timestamp']}")

    return unique_positions


def validate_position_jump(prev_pos, curr_pos, max_speed_kmh=150):
    """
    Check if the jump between two positions is physically reasonable

    Returns: (is_valid, distance_m, implied_speed_kmh)
    """
    if not prev_pos:
        return True, 0, 0

    time_diff = (curr_pos['timestamp_datetime'] - prev_pos['timestamp_datetime']).total_seconds() / 3600

    if time_diff <= 0:
        return True, 0, 0

    # Calculate distance using Haversine formula
    lat1, lon1 = math.radians(prev_pos['latitude_deg']), math.radians(prev_pos['longitude_deg'])
    lat2, lon2 = math.radians(curr_pos['latitude_deg']), math.radians(curr_pos['longitude_deg'])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    a = math.sin(dlat / 2.0)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2.0)**2
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    distance_m = 6371000.0 * c

    max_distance_m = max_speed_kmh * 1000 * time_diff
    implied_speed = (distance_m / 1000) / time_diff if time_diff > 0 else 0

    is_valid = distance_m <= max_distance_m

    return is_valid, distance_m, implied_speed


def validate_all_positions(position_list, max_speed_kmh=150, print_warnings=True):
    """
    Validate all positions in sequence, flag impossible jumps
    """
    invalid_indices = []

    for i in range(1, len(position_list)):
        prev = position_list[i-1]
        curr = position_list[i]

        is_valid, distance_m, implied_speed = validate_position_jump(prev, curr, max_speed_kmh)

        if not is_valid:
            invalid_indices.append(i)
            if print_warnings:
                print(f"\n⚠️  WARNING: Impossible jump at index {i}")
                print(f"   From: {prev['timestamp']}")
                print(f"         ({prev['latitude_deg']:.6f}°N, {prev['longitude_deg']:.6f}°E)")
                print(f"   To:   {curr['timestamp']}")
                print(f"         ({curr['latitude_deg']:.6f}°N, {curr['longitude_deg']:.6f}°E)")
                print(f"   Distance: {distance_m:.0f}m in {(curr['timestamp_datetime'] - prev['timestamp_datetime']).total_seconds()/3600:.1f}h")
                print(f"   Implied speed: {implied_speed:.1f} km/h (max allowed: {max_speed_kmh} km/h)")
        else:
            if print_warnings and implied_speed > 0:
                print(f"  [{i}] {curr['timestamp']}: {distance_m:.0f}m, {implied_speed:.1f} km/h ✓")

    return invalid_indices


def save_decoded_positions(parsed_data, position_list, filename=None):
    """
    Save decoded positions to JSON file for inspection
    """
    if not filename:
        filename = f"decoded_positions_{parsed_data['imei']}.json"

    output = {
        'imei': parsed_data['imei'],
        'base_timestamp': parsed_data['timestamp'],
        'total_entries': len(parsed_data['entries']),
        'valid_entries': len(position_list),
        'positions': [
            {
                'index': i,
                'timestamp': pos['timestamp'],
                'latitude': pos['latitude_deg'],
                'longitude': pos['longitude_deg'],
                'speed_kmh': pos['speed_kmh'],
                'differential': pos.get('differential_coords', False)
            }
            for i, pos in enumerate(position_list)
        ]
    }

    with open(filename, 'w') as f:
        json.dump(output, f, indent=2, default=str)

    print(f"\n✓ Decoded positions saved to: {filename}")
    return filename


def process_sms(hex_string, save_json=True, validate=True):
    """
    Complete SMS processing pipeline with all fixes

    Returns: processed position list ready to send to Traccar
    """
    print(f"\n{'='*70}")
    print(f"Processing SMS: {hex_string[:40]}...")
    print(f"{'='*70}")

    parsed = parse_teltonika_sms(hex_string)

    if 'error' in parsed:
        print(f"❌ ERROR: {parsed['error']}")
        return None

    print(f"✓ Parsed successfully")
    print(f"  IMEI: {parsed['imei']}")
    print(f"  Base timestamp: {parsed['timestamp_utc']}")
    print(f"  Total entries: {parsed['element_count']}")

    valid_positions = filter_and_sort_positions(parsed)
    print(f"✓ Filtered and sorted")
    print(f"  Valid entries: {len(valid_positions)}/{parsed['element_count']}")

    unique_positions = deduplicate_positions(valid_positions, parsed['imei'])
    if len(unique_positions) < len(valid_positions):
        print(f"✓ Removed {len(valid_positions) - len(unique_positions)} duplicates")
    else:
        print(f"✓ No duplicates found")

    if validate:
        print(f"\n✓ Validating position sequence:")
        invalid_idx = validate_all_positions(unique_positions, print_warnings=True)
        if invalid_idx:
            print(f"\n⚠️  Found {len(invalid_idx)} positions with impossible jumps")

    if save_json:
        save_decoded_positions(parsed, unique_positions)

    print(f"\n{'='*70}")
    print(f"READY TO SEND: {len(unique_positions)} positions to Traccar")
    print(f"{'='*70}\n")

    return {
        'imei': parsed['imei'],
        'positions': unique_positions
    }


if __name__ == '__main__':
    sms_hex = "04C0743932C00000804CEC6B018E0200000140E9D530E7A8"
    result = process_sms(sms_hex, save_json=False, validate=True)
