#!/usr/bin/env python3
"""
Analyze motion recording data to determine optimal detection parameters
"""
import json
import numpy as np
from pathlib import Path
import sys

def analyze_motion_file(filepath):
    """Analyze a single motion recording file"""
    with open(filepath, 'r') as f:
        data = json.load(f)

    motion_type = data['motion_type']
    samples = data['samples']

    # Extract all values
    x_vals = [s['x'] for s in samples]
    y_vals = [s['y'] for s in samples]
    z_vals = [s['z'] for s in samples]
    dx_vals = [s['delta_x'] for s in samples]
    dy_vals = [s['delta_y'] for s in samples]
    dz_vals = [s['delta_z'] for s in samples]
    acc_vals = [s['acceleration'] for s in samples]

    # Calculate statistics
    print(f"\n{'='*60}")
    print(f"MOTION TYPE: {motion_type}")
    print(f"{'='*60}")
    print(f"Duration: {data['duration_ms']}ms")
    print(f"Samples: {data['sample_count']}")

    print(f"\n--- Acceleration Statistics ---")
    print(f"Mean: {np.mean(acc_vals):.2f}")
    print(f"Median: {np.median(acc_vals):.2f}")
    print(f"Std Dev: {np.std(acc_vals):.2f}")
    print(f"Min: {np.min(acc_vals):.2f}")
    print(f"Max: {np.max(acc_vals):.2f}")
    print(f"95th percentile: {np.percentile(acc_vals, 95):.2f}")
    print(f"99th percentile: {np.percentile(acc_vals, 99):.2f}")

    print(f"\n--- Delta X (Horizontal) ---")
    print(f"Mean: {np.mean(dx_vals):.2f}")
    print(f"Max: {np.max(dx_vals):.2f}")
    print(f"95th percentile: {np.percentile(dx_vals, 95):.2f}")

    print(f"\n--- Delta Y (Vertical) ---")
    print(f"Mean: {np.mean(dy_vals):.2f}")
    print(f"Max: {np.max(dy_vals):.2f}")
    print(f"95th percentile: {np.percentile(dy_vals, 95):.2f}")

    print(f"\n--- Delta Z (Depth) ---")
    print(f"Mean: {np.mean(dz_vals):.2f}")
    print(f"Max: {np.max(dz_vals):.2f}")
    print(f"95th percentile: {np.percentile(dz_vals, 95):.2f}")

    # Find peaks (significant motion events)
    threshold = np.percentile(acc_vals, 90)
    peaks = [i for i, a in enumerate(acc_vals) if a > threshold]
    print(f"\n--- Peak Detection (>90th percentile = {threshold:.2f}) ---")
    print(f"Number of peaks: {len(peaks)}")

    if peaks:
        print(f"\nTop 5 peaks:")
        peak_data = [(i, acc_vals[i], dx_vals[i], dy_vals[i], dz_vals[i])
                     for i in peaks]
        peak_data.sort(key=lambda x: x[1], reverse=True)

        for idx, (i, acc, dx, dy, dz) in enumerate(peak_data[:5]):
            print(f"  {idx+1}. time={samples[i]['time_ms']}ms, "
                  f"acc={acc:.2f}, dx={dx:.2f}, dy={dy:.2f}, dz={dz:.2f}")

    # Axis dominance
    print(f"\n--- Axis Dominance Analysis ---")
    dx_dominant = sum(1 for i in range(len(dx_vals))
                     if dx_vals[i] > dy_vals[i] and dx_vals[i] > dz_vals[i])
    dy_dominant = sum(1 for i in range(len(dy_vals))
                     if dy_vals[i] > dx_vals[i] and dy_vals[i] > dz_vals[i])
    dz_dominant = sum(1 for i in range(len(dz_vals))
                     if dz_vals[i] > dx_vals[i] and dz_vals[i] > dy_vals[i])

    total = len(dx_vals)
    print(f"X-dominant: {dx_dominant} ({100*dx_dominant/total:.1f}%)")
    print(f"Y-dominant: {dy_dominant} ({100*dy_dominant/total:.1f}%)")
    print(f"Z-dominant: {dz_dominant} ({100*dz_dominant/total:.1f}%)")

    return {
        'motion_type': motion_type,
        'mean_acc': np.mean(acc_vals),
        'median_acc': np.median(acc_vals),
        'max_acc': np.max(acc_vals),
        'p95_acc': np.percentile(acc_vals, 95),
        'mean_dx': np.mean(dx_vals),
        'mean_dy': np.mean(dy_vals),
        'mean_dz': np.mean(dz_vals),
        'max_dx': np.max(dx_vals),
        'max_dy': np.max(dy_vals),
        'max_dz': np.max(dz_vals),
        'p95_dx': np.percentile(dx_vals, 95),
        'p95_dy': np.percentile(dy_vals, 95),
        'p95_dz': np.percentile(dz_vals, 95),
        'x_dominance': dx_dominant/total,
        'y_dominance': dy_dominant/total,
        'z_dominance': dz_dominant/total,
        'num_peaks': len(peaks)
    }

def main():
    # Find all motion recording files
    motion_files = list(Path('.').glob('Motion Recording_*.json'))

    if not motion_files:
        print("No motion recording files found!")
        return

    print(f"Found {len(motion_files)} motion recording files")

    all_stats = []
    for filepath in sorted(motion_files):
        stats = analyze_motion_file(filepath)
        all_stats.append(stats)

    # Summary comparison
    print(f"\n{'='*60}")
    print("SUMMARY COMPARISON")
    print(f"{'='*60}")
    print(f"\n{'Motion':<15} {'Mean Acc':<10} {'P95 Acc':<10} {'Max Acc':<10} {'Dominant Axis'}")
    print("-" * 70)

    for stats in all_stats:
        dominant = 'X' if stats['x_dominance'] > max(stats['y_dominance'], stats['z_dominance']) else \
                  'Y' if stats['y_dominance'] > stats['z_dominance'] else 'Z'
        print(f"{stats['motion_type']:<15} {stats['mean_acc']:<10.2f} "
              f"{stats['p95_acc']:<10.2f} {stats['max_acc']:<10.2f} {dominant}")

    # Recommended thresholds
    print(f"\n{'='*60}")
    print("RECOMMENDED DETECTION THRESHOLDS")
    print(f"{'='*60}")

    # Group by motion type
    motion_groups = {}
    for stats in all_stats:
        mtype = stats['motion_type']
        if mtype not in motion_groups:
            motion_groups[mtype] = []
        motion_groups[mtype].append(stats)

    for mtype, group in motion_groups.items():
        print(f"\n{mtype}:")
        avg_p95_acc = np.mean([s['p95_acc'] for s in group])
        avg_p95_dx = np.mean([s['p95_dx'] for s in group])
        avg_p95_dy = np.mean([s['p95_dy'] for s in group])
        avg_p95_dz = np.mean([s['p95_dz'] for s in group])

        print(f"  Total acceleration threshold: {avg_p95_acc * 0.7:.2f}")
        print(f"  Delta X threshold: {avg_p95_dx * 0.7:.2f}")
        print(f"  Delta Y threshold: {avg_p95_dy * 0.7:.2f}")
        print(f"  Delta Z threshold: {avg_p95_dz * 0.7:.2f}")

if __name__ == '__main__':
    main()
