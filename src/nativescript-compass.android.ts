import { Application } from "@nativescript/core";
import {
  CompassBase,
  CompassOptions,
  CompassReading,
  CompassCallback,
  CompassErrorCallback,
} from "./nativescript-compass.common";

declare const org: any;

export class Compass extends CompassBase {
  private static compassHelper: any = null;
  private static currentCallback: CompassCallback | null = null;
  private static currentErrorCallback: CompassErrorCallback | null = null;

  static isAvailable(): boolean {
    try {
      const activity =
        Application.android.foregroundActivity ||
        Application.android.startActivity;
      if (!activity) return false;

      return org.nativescript.compass.CompassHelper.isCompassAvailable(
        activity,
      );
    } catch (error) {
      return false;
    }
  }

  static async startUpdating(
    options: CompassOptions,
    onReading: CompassCallback,
    onError?: CompassErrorCallback,
  ): Promise<boolean> {
    return new Promise<boolean>((resolve, reject) => {
      try {
        const activity =
          Application.android.foregroundActivity ||
          Application.android.startActivity;

        if (!activity) {
          reject("No Android activity found");
          return;
        }

        if (!this.isAvailable()) {
          reject("Compass not available on this device");
          return;
        }

        // Stop any existing compass updates
        this.stopUpdating();

        // Store callbacks
        this.currentCallback = onReading;
        this.currentErrorCallback = onError;

        // Apply default options
        const finalOptions = {
          minChangeThreshold: options.minChangeThreshold ?? 3,
          updateThrottle: options.updateThrottle ?? 200,
          filter: options.filter ?? 0.8,
          android: {
            useSensorFusion: options.android?.useSensorFusion ?? true,
            sensorDelay: options.android?.sensorDelay ?? "ui",
          },
        };

        // Validate options
        if (
          finalOptions.minChangeThreshold < 0.1 ||
          finalOptions.minChangeThreshold > 180
        ) {
          reject("minChangeThreshold must be between 0.1 and 180 degrees");
          return;
        }
        if (
          finalOptions.updateThrottle < 50 ||
          finalOptions.updateThrottle > 5000
        ) {
          reject("updateThrottle must be between 50 and 5000 ms");
          return;
        }
        if (finalOptions.filter < 0 || finalOptions.filter > 1) {
          reject("filter must be between 0.0 and 1.0");
          return;
        }

        // Create native callback
        const callback = new org.nativescript.compass.CompassCallback({
          onReading(
            heading: number,
            accuracy: number,
            magneticHeading: number,
            timestamp: number,
          ) {
            if (Compass.currentCallback) {
              const reading: CompassReading = {
                heading: heading,
                accuracy: accuracy,
                timestamp: timestamp,
                magneticHeading:
                  magneticHeading !== heading ? magneticHeading : undefined,
              };
              Compass.currentCallback(reading);
            }
          },
          onError(error: string) {
            if (Compass.currentErrorCallback) {
              Compass.currentErrorCallback(error);
            }
          },
        });

        // Create and start compass helper
        this.compassHelper = new org.nativescript.compass.CompassHelper(
          activity,
          finalOptions.minChangeThreshold,
          finalOptions.updateThrottle,
          finalOptions.filter,
          finalOptions.android.useSensorFusion,
          finalOptions.android.sensorDelay,
          callback,
        );

        const started = this.compassHelper.startUpdating();
        resolve(started);
      } catch (error) {
        reject(error.toString());
      }
    });
  }

  static stopUpdating(): boolean {
    try {
      if (this.compassHelper) {
        const stopped = this.compassHelper.stopUpdating();
        this.compassHelper = null;
        this.currentCallback = null;
        this.currentErrorCallback = null;
        return stopped;
      }
      return true;
    } catch (error) {
      return false;
    }
  }

  static async getCurrentReading(
    options?: CompassOptions,
  ): Promise<CompassReading> {
    return new Promise<CompassReading>((resolve, reject) => {
      try {
        const activity =
          Application.android.foregroundActivity ||
          Application.android.startActivity;

        if (!activity) {
          reject("No Android activity found");
          return;
        }

        if (!this.isAvailable()) {
          reject("Compass not available on this device");
          return;
        }

        // Apply default options
        const finalOptions = {
          android: {
            useSensorFusion: options?.android?.useSensorFusion ?? true,
            sensorDelay: options?.android?.sensorDelay ?? "ui",
          },
        };

        // Create callback for single reading
        const callback = new org.nativescript.compass.CompassCallback({
          onReading(
            heading: number,
            accuracy: number,
            magneticHeading: number,
            timestamp: number,
          ) {
            const reading: CompassReading = {
              heading: heading,
              accuracy: accuracy,
              timestamp: timestamp,
              magneticHeading:
                magneticHeading !== heading ? magneticHeading : undefined,
            };
            resolve(reading);
          },
          onError(error: string) {
            reject(error);
          },
        });

        // Get single reading
        org.nativescript.compass.CompassHelper.getCurrentReading(
          activity,
          finalOptions.android.useSensorFusion,
          finalOptions.android.sensorDelay,
          callback,
        );
      } catch (error) {
        reject(error.toString());
      }
    });
  }
}
