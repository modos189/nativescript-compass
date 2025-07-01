export interface CompassOptions {
  // Minimum change in degrees to trigger callback (after filtering)
  minChangeThreshold?: number; // default: 3

  // Minimum interval between callbacks in milliseconds
  updateThrottle?: number; // default: 200

  // Smoothing filter coefficient: 0.0 = no filter, 1.0 = maximum smoothing
  filter?: number; // default: 0.8

  android?: {
    useSensorFusion?: boolean; // Use gyroscope + accelerometer/magnetometer (default: true)
    sensorDelay?: "fastest" | "game" | "ui" | "normal"; // Sensor polling rate (default: 'ui')
  };

  ios?: {
    usesTrueHeading?: boolean; // Use true north instead of magnetic (default: false)
    headingFilter?: number; // minimum degrees change for hardware update (default: minChangeThreshold)
  };
}

export interface CompassReading {
  heading: number; // 0-360 degrees from north
  accuracy: number; // Accuracy in degrees (lower = better)
  timestamp: number; // Reading timestamp

  // Optional fields (only if available)
  magneticHeading?: number; // If different from heading
  trueHeading?: number; // Only on iOS if usesTrueHeading=true
}

export type CompassCallback = (reading: CompassReading) => void;
export type CompassErrorCallback = (error: string) => void;

export abstract class CompassBase {
  static isAvailable(): boolean {
    throw new Error(
      "Compass.isAvailable() must be implemented in platform-specific code",
    );
  }

  static startUpdating(
    options: CompassOptions,
    onReading: CompassCallback,
    onError?: CompassErrorCallback,
  ): Promise<boolean> {
    throw new Error(
      "Compass.startUpdating() must be implemented in platform-specific code",
    );
  }

  static stopUpdating(): boolean {
    throw new Error(
      "Compass.stopUpdating() must be implemented in platform-specific code",
    );
  }

  static getCurrentReading(options?: CompassOptions): Promise<CompassReading> {
    throw new Error(
      "Compass.getCurrentReading() must be implemented in platform-specific code",
    );
  }
}
