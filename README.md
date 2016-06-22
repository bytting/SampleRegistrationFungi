# SampleRegistrationFungi

Android application for registration of fungi samples in the field.

The application creates a folder named "sampleregistrationfungi" under the DOCUMENTS system folder.
The sampleregistrationfungi folder contains two new folders, "projects" and "config".

The projects folder contains the log files for each project.

The format of the log file is as follows:

The first line in the file is a unique ID for the file.
The remaining lines are | separated fields with the following format:

Phone ID | Sample ID | Collector | Collector address | Project name | Date/Time (ISO) | Latitude | Longitude | Above sea level (WGS84) | Sample type | Location | Location type | County/Community | Adjacent hardwoods | Grass density | Herbs Density | Heather density | Sample type density | Satellite count | Accuracy (meters) | Sample receiver | Sample comment

If there is a file called sample-types.txt under the config folder, this file will be used as a
suggestion database for the sample type field. The sample-types.txt file should have one sample type per line.

If there is a file called locations.txt under the config folder, this file will be used as a
suggestion database for the location field. The location.txt file should have one unit per line.

If there is a file called location_types.txt under the config folder, this file will be used as a
suggestion database for the location type field. The location_types.txt file should have one unit per line.
If this file does not exist, a default file will be created at program startup.

If there is a file called adjacent_hardwoods.txt under the config folder, this file will be used as a
suggestion database for the adjacent hardwoods field. The adjacent_hardwoods.txt file should have one unit per line.
If this file does not exist, a default file will be created at program startup.

If there is a file called communities.txt under the config folder, this file will be used as a
suggestion database for the county/community field. The communities.txt file should have two comma separated units per line: "community", "county"

All configuration files must have UTF-8 format.

__Status:__

Development

__License:__

GPL3