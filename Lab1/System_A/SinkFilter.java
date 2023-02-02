import java.util.*;						// This class is used to interpret time words
import java.text.SimpleDateFormat;		// This class is used to format and write time in a string format.
import java.io.FileWriter;				// This class is used to write to the output csv file
import java.io.IOException;				// This class is used to handle errors with the file

public class SinkFilter extends FilterFramework
{
	public void run()
    {
		/************************************************************************************
		*	TimeStamp is used to compute time using java.util's Calendar class.
		* 	TimeStampFormat is used to format the time value so that it can be easily printed
		*	to the terminal.
		*************************************************************************************/
		Calendar TimeStamp = Calendar.getInstance();
		SimpleDateFormat TimeStampFormat = new SimpleDateFormat("yyyy:MM:dd:hh:mm:ss:SS");

		FileWriter writer = null;
		try {
			writer = new FileWriter("OutputA.csv");
			writer.write("Time, Velocity, Altitude, Pressure, Temperature\n");		// column names for the csv
		} catch(IOException e) {
			e.printStackTrace();
		}

		int MeasurementLength = 8;		// This is the length of all measurements (including time) in bytes
		int IdLength = 4;				// This is the length of IDs in the byte stream
		byte databyte = 0;				// This is the data byte read from the stream
		int bytesread = 0;				// This is the number of bytes read from the stream
		long measurement;				// This is the word used to store all measurements - conversions are illustrated.
		Double velocity = 0.0;			// This will store the converted measurement to velocity
		Double altitude = 0.0;			// This will store the converted measurement to altitude
		Double pressure = 0.0;			// This will store the converted measurement to pressure
		Double temperature = 0.0;		// This will store the converted measurement to temperature
		int id;							// This is the measurement id
		int i;							// This is a loop counter

		// First we announce to the world that we are alive...
		System.out.print( "\n" + this.getName() + "::Sink Reading ");

		while (true)
		{
			try
			{
				/***************************************************************************
				// We know that the first data coming to this filter is going to be an ID and
				// that it is IdLength long. So we first get the ID bytes.
				****************************************************************************/
				id = 0;
				for (i=0; i<IdLength; i++ )
				{
					databyte = ReadFilterInputPort();	// This is where we read the byte from the stream...
					id = id | (databyte & 0xFF);		// We append the byte on to ID...
					if (i != IdLength-1)				// If this is not the last byte, then slide the
					{									// previously appended byte to the left by one byte
						id = id << 8;					// to make room for the next byte we append to the ID
					}
					bytesread++;						// Increment the byte count
				}

				/****************************************************************************
				// Here we read measurements. All measurement data is read as a stream of bytes
				// and stored as a long value. This permits us to do bitwise manipulation that
				// is neccesary to convert the byte stream into data words. Note that bitwise
				// manipulation is not permitted on any kind of floating point types in Java.
				// If the id = 0 then this is a time value and is therefore a long value - no
				// problem. However, if the id is something other than 0, then the bits in the
				// long value is really of type double and we need to convert the value using
				// Double.longBitsToDouble(long val) to do the conversion which is illustrated below.
				*****************************************************************************/
				measurement = 0;
				for (i=0; i<MeasurementLength; i++ )
				{
					databyte = ReadFilterInputPort();
					measurement = measurement | (databyte & 0xFF);	// We append the byte on to measurement...
					if (i != MeasurementLength-1)					// If this is not the last byte, then slide the
					{												// previously appended byte to the left by one byte
						measurement = measurement << 8;				// to make room for the next byte we append to the
																	// measurement
					}
					bytesread++;									// Increment the byte count
				}

				/****************************************************************************
				// Here we look for an ID of 0 which indicates this is a time measurement.
				// Every frame begins with an ID of 0, followed by a time stamp which correlates
				// to the time that each proceeding measurement was recorded. Time is stored
				// in milliseconds since Epoch. This allows us to use Java's calendar class to
				// retrieve time and also use text format classes to format the output into
				// a form humans can read. So this provides great flexibility in terms of
				// dealing with time arithmetically or for string display purposes. This is
				// illustrated below.
				****************************************************************************/
				if ( id == 0 )
				{
					TimeStamp.setTimeInMillis(measurement);
				}

				/****************************************************************************
				// Here we look for an ID of 1 which indicates this is a velocity measurement.
				****************************************************************************/
				if ( id == 1 )
				{
					velocity = Double.longBitsToDouble(measurement);
				}

				/****************************************************************************
				// Here we look for an ID of 2 which indicates this is an altitude measurement.
				****************************************************************************/
				if ( id == 2 )
				{
					altitude = Double.longBitsToDouble(measurement);
				}

				/****************************************************************************
				// Here we look for an ID of 3 which indicates this is a pressure measurement.
				****************************************************************************/
				if ( id == 3 )
				{
					pressure = Double.longBitsToDouble(measurement);
				}

				/****************************************************************************
				// Here we look for an ID of 4 which indicates this is a temperature measurement.
				// We have reached the end of Frame at this point, so all of the data can now
				// be written to the csv file
				****************************************************************************/
				if ( id == 4 )
				{
					temperature = Double.longBitsToDouble(measurement);

					try {
						writer.write(TimeStampFormat.format(TimeStamp.getTime()) + ",");
						writer.write(velocity + ",");
						writer.write(altitude + ",");
						writer.write(pressure + ",");
						writer.write(temperature + "\n");
					} catch(IOException e) {
						e.printStackTrace();
					}
				}
			}
			/*******************************************************************************
			*	The EndOfStreamExeception below is thrown when you reach end of the input
			*	stream. At this point, the filter ports and FileWriter are closed and a message 
			*	written letting the user know what is going on.
			********************************************************************************/
			catch (EndOfStreamException e)
			{
				try {
					writer.close();
				} catch (IOException error) {
					error.printStackTrace();
				}
				ClosePorts();
				System.out.print( "\n" + this.getName() + "::Sink Exiting; bytes read: " + bytesread );
				break;
			}
		} // while
   } // run
}