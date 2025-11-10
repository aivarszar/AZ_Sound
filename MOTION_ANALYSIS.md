# Motion Detection Analysis & Algorithm Updates

## 📊 Data Analysis Results

Based on real user recordings from the Motion Sounds app, the following motion patterns were identified:

### 1. DROPPING (Nomešana)
**Characteristics:**
- **Average Acceleration:** 3.0
- **Max Acceleration:** 85.14
- **Duration:** ~26 seconds, 227 samples
- **Key Pattern:** Z-axis dominant with very high delta_z values (20-85)
- **Top Peak:** time=12956ms, acc=85.14, dx=22.19, dy=1.90, dz=82.18

**Detection Logic:**
```kotlin
deltaZ > 20f && acceleration > 20f
```

### 2. SCRATCHING (Ādas kasīšana)
**Characteristics:**
- **Average Acceleration:** 2.3 - 3.0
- **Max Acceleration:** 12.24 - 94.44
- **Duration:** 8-15 seconds, 71-130 samples
- **Key Pattern:** Z-dominant with mixed X/Y movement, burst patterns
- **Top Peaks:** Acceleration spikes 8-25 with significant Z-delta

**Detection Logic:**
```kotlin
acceleration > 8f && deltaZ > 8f
```

### 3. SWINGING (Šūpoles)
**Characteristics:**
- **Average Acceleration:** 0.72 (LOWEST!)
- **Max Acceleration:** 2.44
- **Duration:** ~18 seconds, 151 samples
- **Key Pattern:** Low, steady, rhythmic motion
- **Top Peaks:** Gentle consistent movements across all axes

**Detection Logic:**
```kotlin
acceleration > 1.5f && acceleration < 2.5f && hasMovement
```

### 4. WHOOSHING (Švīkstoņa)
**Characteristics:**
- **Average Acceleration:** 1.19
- **Max Acceleration:** 4.73
- **Duration:** ~17 seconds, 151 samples
- **Key Pattern:** X-axis dominant horizontal swing
- **Top Peak:** time=11647ms, acc=4.73, dx=4.31, dy=1.94, dz=0.10

**Detection Logic:**
```kotlin
deltaX > 3f && deltaX > deltaY && deltaX > deltaZ && acceleration 3-10
```

## 🔧 Algorithm Updates

### Priority Order
The detection now checks motions in order of distinctiveness to avoid false positives:

1. **DROPPING** - Most distinctive (highest acceleration + Z-delta)
2. **THROWING** - High upward acceleration
3. **SCRATCHING** - Z-dominant bursts
4. **WHOOSHING** - X-dominant swing
5. **SWINGING** - Lowest threshold, checked last

### Threshold Values (Medium Sensitivity)
```kotlin
scratchingThreshold = 8f      // Z-dominant bursts
swingingThreshold = 1.5f       // Low steady motion
throwThreshold = 15f           // Upward acceleration
dropThreshold = 20f            // High Z downward
whooshThreshold = 3f           // X-dominant swing
```

### Sensitivity Multipliers
- **Low:** 0.7x
- **Medium:** 1.0x (baseline from data)
- **High:** 1.5x

## 📈 Improvements

1. **Data-Driven:** All thresholds based on actual user recordings
2. **Prioritized Detection:** Most distinctive patterns checked first
3. **Axis-Specific:** Each motion has clear axis dominance pattern
4. **False Positive Reduction:** Lower idle threshold (0.5 vs 2.0)
5. **Better Sensitivity:** Swinging threshold drastically lowered (1.5 vs 8.0)

## 🎯 Expected Improvements

- **Swinging:** Should be much easier to trigger (0.72 avg vs old 8.0 threshold)
- **Whooshing:** More accurate X-axis detection
- **Dropping:** Better differentiation from other motions
- **Scratching:** More reliable Z-burst detection
- **Throwing:** Clearer upward motion detection

## 📝 Testing Recommendations

1. Test each motion type with different sensitivity levels
2. Record new samples if detection seems off
3. Adjust thresholds based on device-specific behavior
4. Consider adding machine learning for personalized detection

---

*Analysis performed on: 2025-11-10*
*Data source: samples.zip (5 motion recording sessions)*
