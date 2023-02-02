import java.util.*;						// This class is used to interpret time words
import java.io.FileWriter;				// This class is used to write to the output csv file
import java.io.IOException;				// This class is used to handle errors with files
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;		// This class is used to format and write time in a string format.

public class MiddleFilter extends FilterFramework
{
	public void run()
    {
		Calendar TimeStamp = Calendar.getInstance();
		SimpleDateFormat TimeStampFormat = new SimpleDateFormat("yyyy:dd:hh:mm:ss");

		FileWriter writer = null;
		try {
			writer = new FileWriter("WildPoints.csv");
			writer.write("Time, Velocity, Altitude, Pressure, Temperature\n");		// column names for the csv
		} catch(IOException e) {
			e.printStackTrace();
		}

		int bytesread = 0;					// Number of bytes read from the input file.
		int byteswritten = 0;				// Number of bytes written to the stream.
		byte databyte = 0;					// The byte of data read from the file
		int MeasurementLength = 8;			// This is the length of all measurements (including time) in bytes
		int IdLength = 4;					// This is the length of IDs in the byte stream
		long measurement;					// This is the word used to store all measurements - conversions are illustrated.
		int id;								// This is the measurement id
		int i;								// This is a loop counter
		Double velocity = 0.0;				// This will store the converted measurement to velocity
		Double altitude = 0.0;				// This will store the converted measurement to altitude
		Double pressure = 0.0;				// This will store the converted measurement to pressure
		Double temperature = 0.0;			// This will store the converted measurement to temperature
		Double prevAltitude = 0.0;			// The previous altitude measurement (to check for wild jump)
		Double last2Avg = 0.0;				// Average of the previous two altitude measurements
		Double replacedAlt = 0.0;			// Altitude that was replace (should be output to WildPoints.csv)
		boolean altUpdate = false;			// boolean keeping track if the altitude has been updated

		// Next we write a message to the terminal to let the world know we are alive...
		System.out.print( "\n" + this.getName() + "::Middle Reading ");

		while (true)
		{
			// Here we read a byte and write a byte
			try
			{

				/************************************************************************************
				*	Similar to the SinkFilter, we read in the data to check if the altitude measurement
				*	had a wild jump
				*************************************************************************************/
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
				
				if ( id == 0 )
				{
					TimeStamp.setTimeInMillis(measurement);
				}
				else if ( id == 1 )
				{
					velocity = Double.longBitsToDouble(measurement);
				}
				else if ( id == 2 )
				{
					altitude = Double.longBitsToDouble(measurement);
					if(prevAltitude != 0.0 && (altitude - prevAltitude > 100 || altitude - prevAltitude < -100))	// check for wild jump 
					{
						altUpdate = true;
						id = 5;		// change the id so that the sink filter knows altitude has been changed
						replacedAlt = altitude;
						if(last2Avg == 0.0)							// case for not yet having two previous altitudes
						{
							altitude = prevAltitude;
						}
						else
						{
							altitude = last2Avg;
						}
					}

					if(prevAltitude != 0.0)
					{
						last2Avg = (prevAltitude + altitude) / 2.0;	// if there was a previous altitude, update the avg
					}
					prevAltitude = altitude;						// can now update previous altitude
				}
				else if ( id == 3 )
				{
					pressure = Double.longBitsToDouble(measurement);
				}
				else if (id == 4)
				{
					temperature = Double.longBitsToDouble(measurement);

					if(altUpdate)
					{
						// write the wild jump record to WildPoints.csv
						try {
							writer.write(TimeStampFormat.format(TimeStamp.getTime()) + ",");
							writer.write(velocity + ",");
							writer.write(replacedAlt + ",");
							writer.write(pressure + ",");
							writer.write(temperature + "\n");
						} catch(IOException e) {
							e.printStackTrace();
						}
					}
					altUpdate = false;			// reset back to false
				}

				// pass the last 12 bytes (id and measurement) to the SinkFilter
				ByteBuffer buffer = ByteBuffer.allocate(12);
				buffer.putInt(id);
				if(id == 5)							// if the altitude has been updated place it in the buffer
					buffer.putDouble(altitude);
				else 
					buffer.putLong(measurement);
				buffer.rewind();
				while(buffer.hasRemaining())
				{
					WriteFilterOutputPort(buffer.get());	// pass upstream, byte by byte
				}
			}
			catch (EndOfStreamException e)
			{
				try {
					writer.close();				// close the FileWriter for WildPoints.csv
				} catch (IOException error) {
					error.printStackTrace();
				}
				ClosePorts();
				System.out.print( "\n" + this.getName() + "::Middle Exiting; bytes read: " + bytesread + " bytes written: " + byteswritten );
				break;
			}
		}
   }
}