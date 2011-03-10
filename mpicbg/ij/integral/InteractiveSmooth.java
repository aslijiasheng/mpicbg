/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package mpicbg.ij.integral;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * 
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class InteractiveSmooth implements KeyListener, MouseListener, MouseMotionListener, PlugIn
{
	final static private String NL = System.getProperty( "line.separator" );
	
	private int blockRadiusX = 0, blockRadiusY = 0;
	private ImageJ ij;
	private ImagePlus imp;
	private ImageWindow window;
	private Canvas canvas;
	private IntegralImage integral;
	private PaintThread painter;
	
	@Override
	public void run( String arg )
	{
		ij = IJ.getInstance();
		imp = IJ.getImage();
		window = imp.getWindow();
		canvas = imp.getCanvas();
		
		canvas.addKeyListener( this );
		window.addKeyListener( this );
		canvas.addMouseMotionListener( this );
		canvas.addMouseListener( this );
		ij.addKeyListener( this );
		
		switch( imp.getType() )
		{
		case ImagePlus.GRAY32:
			integral = new DoubleIntegralImage( imp.getProcessor() );
			break;
		case ImagePlus.GRAY8:
		case ImagePlus.GRAY16:
			integral = new LongIntegralImage( imp.getProcessor() );
			break;
		case ImagePlus.COLOR_RGB:
			integral = new LongRGBIntegralImage( ( ColorProcessor )imp.getProcessor() );
			break;
		default:
			IJ.error( "Type not yet supported." );
			return;
		}
		
		imp.getProcessor().snapshot();
		
		Toolbar.getInstance().setTool( Toolbar.RECTANGLE );
		
		painter = new PaintThread();
		painter.start();
		
	}
	
	final private void draw()
	{
		final ImageProcessor ip = imp.getProcessor();
		final int w = imp.getWidth() - 1;
		final int h = imp.getHeight() - 1;
		for ( int y = 0; y <= h; ++y )
		{
			final int yMin = Math.max( -1, y - blockRadiusY - 1 );
			final int yMax = Math.min( h, y + blockRadiusY );
			final int bh = yMax - yMin;
			for ( int x = 0; x <= w; ++x )
			{
				final int xMin = Math.max( -1, x - blockRadiusX - 1 );
				final int xMax = Math.min( w, x + blockRadiusX );
				final float scale = 1.0f / ( xMax - xMin ) / bh;
				ip.set( x, y, integral.getScaledSum( xMin, yMin, xMax, yMax, scale ) );
			}
		}
	}
	
	public class PaintThread extends Thread
	{
		private boolean pleaseRepaint;
		
		PaintThread()
		{
			this.setName( "MappingThread" );
		}
		
		@Override
		public void run()
		{
			while ( !isInterrupted() )
			{
				final boolean b;
				synchronized ( this )
				{
					b = pleaseRepaint;
					pleaseRepaint = false;
				}
				if ( b )
				{
					draw();
					imp.updateAndDraw();
				}
				synchronized ( this )
				{
					try
					{
						if ( !pleaseRepaint ) wait();
					}
					catch ( InterruptedException e ){}
				}
			}
		}
		
		public void repaint()
		{
			synchronized ( this )
			{
				pleaseRepaint = true;
				notify();
			}
		}
	}
	
	public void keyPressed( KeyEvent e )
	{
		if ( e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER )
		{
			painter.interrupt();
			
			canvas.removeKeyListener( this );
			window.removeKeyListener( this );
			ij.removeKeyListener( this );
			canvas.removeMouseListener( this );
			canvas.removeMouseMotionListener( this );
			
			if ( imp != null )
			{
				if ( e.getKeyCode() == KeyEvent.VK_ESCAPE )
				{
					imp.getProcessor().reset();
				}
				else if ( e.getKeyCode() == KeyEvent.VK_ENTER )
				{
				}
			}
			imp.updateAndDraw();
		}
		else if ( e.getKeyCode() == KeyEvent.VK_F1 )
		{
			IJ.showMessage(
					"Interactive Mean Smooth",
					"Click and drag to change the size of the smoothing kernel." + NL +
					"ENTER - Apply" + NL +
					"ESC - Cancel" );
		}
	}
	
	public void keyReleased( KeyEvent e ) {}
	public void keyTyped( KeyEvent e ) {}

	public void mouseDragged( final MouseEvent e )
	{
		final Roi roi = imp.getRoi();
		if ( roi != null )
		{
			final Rectangle bounds = imp.getRoi().getBounds();
			blockRadiusX = bounds.width / 2;
			blockRadiusY = bounds.height / 2;
		}
		else
		{
			blockRadiusX = 0;
			blockRadiusY = 0;	
		}
		painter.repaint();
	}

	public void mouseMoved( MouseEvent e ){}
	public void mouseClicked( MouseEvent e ){}
	public void mouseEntered( MouseEvent e ){}
	public void mouseExited( MouseEvent e ){}
	public void mouseReleased( MouseEvent e ){}
	public void mousePressed( MouseEvent e )
	{
		mouseDragged( e );
	}
}