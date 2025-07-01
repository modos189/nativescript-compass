import {
  CompassBase,
  CompassOptions,
  CompassReading,
  CompassCallback,
  CompassErrorCallback,
} from "./nativescript-compass.common";

declare const CompassHelper: any;

export class Compass extends CompassBase {
  private static compassHelper: any = null;
  private static currentCallback: CompassCallback | null = null;
  private static currentErrorCallback: CompassErrorCallback | null = null;

  static isAvailable(): boolean {
    try {
      return CompassHelper.isCompassAvailable();
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
          ios: {
            usesTrueHeading: options.ios?.usesTrueHeading ?? false,
            headingFilter:
              options.ios?.headingFilter ?? options.minChangeThreshold ?? 3,
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

        // Create completion callback
        const completion = (
          heading: number,
          accuracy: number,
          magneticHeading: number,
          trueHeading: number,
          timestamp: number,
          error: string,
        ) => {
          if (error) {
            if (Compass.currentErrorCallback) {
              Compass.currentErrorCallback(error);
            }
          } else if (Compass.currentCallback) {
            const reading: CompassReading = {
              heading: heading,
              accuracy: accuracy,
              timestamp: timestamp,
              magneticHeading:
                magneticHeading !== heading ? magneticHeading : undefined,
              trueHeading:
                finalOptions.ios.usesTrueHeading && trueHeading !== heading
                  ? trueHeading
                  : undefined,
            };
            Compass.currentCallback(reading);
          }
        };

        // Start compass updates
        const started = CompassHelper.startUpdatingWithOptionsCompletion(
          finalOptions.minChangeThreshold,
          finalOptions.updateThrottle,
          finalOptions.filter,
          finalOptions.ios.usesTrueHeading,
          finalOptions.ios.headingFilter,
          completion,
        );

        resolve(started);
      } catch (error) {
        reject(error.toString());
      }
    });
  }

  static stopUpdating(): boolean {
    try {
      if (CompassHelper) {
        const stopped = CompassHelper.stopUpdating();
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
        if (!this.isAvailable()) {
          reject("Compass not available on this device");
          return;
        }

        // Apply default options
        const finalOptions = {
          ios: {
            usesTrueHeading: options?.ios?.usesTrueHeading ?? false,
          },
        };

        // Create completion callback for single reading
        const completion = (
          heading: number,
          accuracy: number,
          magneticHeading: number,
          trueHeading: number,
          timestamp: number,
          error: string,
        ) => {
          if (error) {
            reject(error);
          } else {
            const reading: CompassReading = {
              heading: heading,
              accuracy: accuracy,
              timestamp: timestamp,
              magneticHeading:
                magneticHeading !== heading ? magneticHeading : undefined,
              trueHeading:
                finalOptions.ios.usesTrueHeading && trueHeading !== heading
                  ? trueHeading
                  : undefined,
            };
            resolve(reading);
          }
        };

        // Get single reading
        CompassHelper.getCurrentReadingWithOptionsCompletion(
          finalOptions.ios.usesTrueHeading,
          completion,
        );
      } catch (error) {
        reject(error.toString());
      }
    });
  }
}
