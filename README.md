# SampleRegistrationFungi
Android application for registration of fungi samples in the field

The application creates a folder called "sampleregistrationfungi" under the DOCUMENTS system folder.
The sampleregistrationfungi folder contains two new folders, "projects" and "config".

The projects folder contains the log files for each project.

The format of the log file is as follows:

The first line in the file is a unique ID for the file.
The remaining lines are | separated fields with the following format:
Phone ID | Project name | Sample ID | Date/Time (ISO) | Latitude | Longitude | Station | Sample type | Measurement value | Measurement unit | Satellite count | Accuracy (meters) | Sample comment

If there is a file called sample-types.txt under the config folder, this file will be used as a suggestion database for the sample type field.
The sample-types.txt file should have one sample type per line.

If there is a file called units.txt under the config folder, this file will be used as a suggestion database for the unit field.
The units.txt file should have one unit per line.