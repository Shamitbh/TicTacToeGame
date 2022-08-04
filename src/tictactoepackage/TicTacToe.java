/*
 * Java Project - Networked Tic-Tac-Toe Game with GUI/Threading
 * 
 * GAME PLAY & RULES:
 * In order to play game, please run TicTacToe.java and type in "localhost" for IP and "8080" for Port.
 * The server/1st client bundle should start. Then run TicTacToe.java again and type in "localhost" for IP and "8080" for Port again.
 * This should start the 2nd client and now both player 1 GUI and player 2 GUI should open.
 * Player 1 goes first with the x's and player 2 goes 2nd with the o's. Then players will alternate till the game ends.
 * To win, try and make 3 in a row (horizontally, vertically, diagonally) before your opponent.
*/

package tictactoepackage;

import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class TicTacToe implements Runnable {

	// localhost ip and port 22222 to play two players/screens on one machine for testing purposes
	private String ip = "localhost";
	private int port = 8080;
	private Scanner myScanner = new Scanner(System.in);
	private JFrame boardFrame;
	private final int WIDTH = 506;
	private final int HEIGHT = 600; // 527
	
	// Threading
	private Thread gameThread;

	// Graphics
	private Painter painter;
	
	// Networking fields
	private Socket socket;
	private DataOutputStream dos;
	private DataInputStream dis;
	private ServerSocket serverSocket;

	// Image variables/fields
	private BufferedImage board;
	private BufferedImage redX;
	private BufferedImage blueX;
	private BufferedImage redCircle;
	private BufferedImage blueCircle;

	// keep track of the TicTacToe board with String array of spaces
	private String[] spaces = new String[9];

	// boolean fields to understand game play
	private boolean yourTurn = false;
	private boolean circle = true;
	private boolean accepted = false;
	private boolean unableToCommunicateWithOpponent = false;
	private boolean won = false;
	private boolean enemyWon = false;
	private boolean tie = false;

	private int lengthOfSpace = 160;
	private int errors = 0;
	private int firstSpot = -1;
	private int secondSpot = -1;
	private int numPlayers = 0;

	private String waitingForPlayer = "Waiting for another player";
	private String cannotCommunicate = "Unable to communicate with opponent";
	private String wonString = "You won!";
	private String youLostString = "You lost!";
	private String tieString = "Game ends in a tie!";
	private String player1Assignment = "Player 1 will be playing X's";
	private String player2Assignment = "Player 2 will be playing O's";
	
	// Store fonts
	private Font font = new Font("Verdana", Font.BOLD, 32);
	private Font smallerFont = new Font("Verdana", Font.BOLD, 20);
	private Font largerFont = new Font("Verdana", Font.BOLD, 40);
	
	// different ways that a user can win the game of tic-tac-toe - 2D array logic
	private int[][] wins = new int[][] { { 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 }, { 0, 3, 6 }, { 1, 4, 7 }, { 2, 5, 8 }, { 0, 4, 8 }, { 2, 4, 6 } };

	/*
	 * 0, 1, 2 
	 * 3, 4, 5 
	 * 6, 7, 8
	 */

	// Constructor
	public TicTacToe() {
		System.out.println("Please input the IP. For testing purposes, please enter \"localhost\": ");
		ip = myScanner.nextLine();
		System.out.println("Please input the port. For testing purposes, please enter \"8080\": ");
		port = myScanner.nextInt();
		while (port < 1 || port > 65535) {
			System.out.println("The port you entered was invalid, please input another port: ");
			port = myScanner.nextInt();
		}
		
		// load images from resources folder
		loadImages();

		// initialize painter
		painter = new Painter();
		painter.setPreferredSize(new Dimension(WIDTH, HEIGHT));

		// Try connecting as a client, otherwise make a server
		if (!connect()) {
			initializeServer();
		}

		// initialize frame
		boardFrame = new JFrame();
		boardFrame.setTitle("Tic-Tac-Toe - Player #" + numPlayers);
		boardFrame.setContentPane(painter);
		boardFrame.setSize(WIDTH, HEIGHT);
		boardFrame.setLocationRelativeTo(null);
		boardFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		boardFrame.setResizable(false);
		boardFrame.setVisible(true);

		// Threading - needed because the data input and output via DataInputStream/DataOutputStream require threads so they don't accidentally
		// override each other when sending/receiving data from one player's board to the other player's board
		gameThread = new Thread(this, "TicTacToe");
		gameThread.start();
	}

	// run method required because TicTacToe implements Runnable
	public void run() {
		while (true) {
			gameTurn();
			painter.repaint();

			if (!circle && !accepted) {
				listenForServerRequest();
			}

		}
	}

	
	// Graphics - drawing the board and the respective x's and o's to each board via network
	private void drawGraphics(Graphics g) {
		g.drawImage(board, 0, 0, null);
		if (unableToCommunicateWithOpponent) {
			g.setColor(Color.RED);
			g.setFont(smallerFont);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int stringWidth = g2.getFontMetrics().stringWidth(cannotCommunicate);
			g.drawString(cannotCommunicate, WIDTH / 2 - stringWidth / 2, HEIGHT - 50);
			return;
		}
		
		if (accepted) {
			for (int i = 0; i < spaces.length; i++) {
				if (spaces[i] != null) {
					if (spaces[i].equals("X")) {
						if (circle) {
							g.drawImage(redX, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						} else {
							g.drawImage(blueX, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						}
					} else if (spaces[i].equals("O")) {
						if (circle) {
							g.drawImage(blueCircle, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						} else {
							g.drawImage(redCircle, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
						}
					}
				}
			}
			if (won || enemyWon) {
				Graphics2D g2 = (Graphics2D) g;
				g2.setStroke(new BasicStroke(10));
				g.setColor(Color.BLACK);
				g.drawLine(firstSpot % 3 * lengthOfSpace + 10 * firstSpot % 3 + lengthOfSpace / 2, (int) (firstSpot / 3) * lengthOfSpace + 10 * (int) (firstSpot / 3) + lengthOfSpace / 2, secondSpot % 3 * lengthOfSpace + 10 * secondSpot % 3 + lengthOfSpace / 2, (int) (secondSpot / 3) * lengthOfSpace + 10 * (int) (secondSpot / 3) + lengthOfSpace / 2);

				if (won) {
					g.setColor(Color.GREEN);
					g.setFont(largerFont);
					int stringWidth = g2.getFontMetrics().stringWidth(wonString);
					g.drawString(wonString, WIDTH / 2 - stringWidth / 2, HEIGHT - 50);
				} else if (enemyWon) {
					g.setColor(Color.RED);
					g.setFont(largerFont);
					int stringWidth = g2.getFontMetrics().stringWidth(youLostString);
					g.drawString(youLostString, WIDTH / 2 - stringWidth / 2, HEIGHT - 50);
				}
			}
			if (tie) {
				Graphics2D g2 = (Graphics2D) g;
				g.setColor(Color.BLACK);
				g.setFont(largerFont);
				int stringWidth = g2.getFontMetrics().stringWidth(tieString);
				g.drawString(tieString, WIDTH / 2 - stringWidth / 2, HEIGHT - 50);
			}
		} else {
			g.setColor(Color.RED);
			g.setFont(font);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int stringWidth = g2.getFontMetrics().stringWidth(waitingForPlayer);
			g.drawString(waitingForPlayer, WIDTH / 2 - stringWidth / 2, HEIGHT - 50);
			
		}

	}

	private void gameTurn() {
		if (errors >= 10) {
			unableToCommunicateWithOpponent = true;
		}

		if (!yourTurn && !unableToCommunicateWithOpponent) {
			try {
				int space = dis.readInt();
				if (circle) {
					spaces[space] = "X";
				}else {
					spaces[space] = "O";
				}
				checkForEnemyWin();
				checkForTie();
				yourTurn = true;
			} catch (IOException e) {
				e.printStackTrace();
				errors++;
			}
		}
	}

	private void checkForWin() {
		for (int i = 0; i < wins.length; i++) {
			if (circle) { // identify which player it is
				if (spaces[wins[i][0]] == "O" && spaces[wins[i][1]] == "O" && spaces[wins[i][2]] == "O") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					won = true;
				}
			} else {
				if (spaces[wins[i][0]] == "X" && spaces[wins[i][1]] == "X" && spaces[wins[i][2]] == "X") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					won = true;
				}
			}
		}
	}

	private void checkForEnemyWin() {
		for (int i = 0; i < wins.length; i++) {
			if (circle) {
				if (spaces[wins[i][0]] == "X" && spaces[wins[i][1]] == "X" && spaces[wins[i][2]] == "X") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					enemyWon = true;
				}
			} else {
				if (spaces[wins[i][0]] == "O" && spaces[wins[i][1]] == "O" && spaces[wins[i][2]] == "O") {
					firstSpot = wins[i][0];
					secondSpot = wins[i][2];
					enemyWon = true;
				}
			}
		}
	}

	private void checkForTie() {
		// go through all spaces and if none are null and obviously no one has won yet, has to be tie
		for (int i = 0; i < spaces.length; i++) {
			if (spaces[i] == null) {
				return;
			}
		}
		tie = true;
	}

	private void listenForServerRequest() {
		Socket socket = null;
		try {
			socket = serverSocket.accept();
			dos = new DataOutputStream(socket.getOutputStream());
			dis = new DataInputStream(socket.getInputStream());
			accepted = true;
			System.out.println("Client requested to join and we accepted. 2 Players have connected.");
			System.out.println(player1Assignment);
			System.out.println(player2Assignment);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean connect() {
		try {
			socket = new Socket(ip, port);
			dos = new DataOutputStream(socket.getOutputStream());
			dis = new DataInputStream(socket.getInputStream());
			accepted = true;
			numPlayers = 2;
		} catch (IOException e) {
			System.out.println("Unable to connect to the address: " + ip + ":" + port + " | Starting a server instead.");
			numPlayers = 1;
			return false;
		}
		System.out.println("Successfully connected to the server.");
		return true;
	}

	private void initializeServer() {
		try {
			serverSocket = new ServerSocket(port, 8, InetAddress.getByName(ip));
		} catch (Exception e) {
			e.printStackTrace();
		}
		yourTurn = true;
		circle = false;
	}

	private void loadImages() {
		try {
			board = ImageIO.read(getClass().getResourceAsStream("/game-board-picture copy.png"));
			redX = ImageIO.read(getClass().getResourceAsStream("/x-red.png"));
			redCircle = ImageIO.read(getClass().getResourceAsStream("/circle-red.png"));
			blueX = ImageIO.read(getClass().getResourceAsStream("/x-blue.png"));
			blueCircle = ImageIO.read(getClass().getResourceAsStream("/circle-blue.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Inner class for paint component graphics + mouse event listener 
	private class Painter extends JPanel implements MouseListener {

		public Painter() {
			setFocusable(true);
			requestFocus();
			setBackground(Color.WHITE);
			addMouseListener(this);
		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			drawGraphics(g);
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			if (accepted) {
				if (yourTurn && !unableToCommunicateWithOpponent && !won && !enemyWon) {
					int x = e.getX() / lengthOfSpace;
					int y = e.getY() / lengthOfSpace;
					y *= 3; // so the index in spaces array is correct
					int position = x + y;

					if (spaces[position] == null) {
						if (!circle) {
							spaces[position] = "X";
						}else {
							spaces[position] = "O";
						}
						yourTurn = false;
						repaint();
						Toolkit.getDefaultToolkit().sync(); // makes sure animation/display is up to date

						// Send data of position back to server
						try {
							dos.writeInt(position);
							dos.flush();
						} catch (IOException ex) {
							errors++;
							ex.printStackTrace();
						}

						// System.out.println("data has been sent");
						checkForWin();
						checkForTie();

					}
				}
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {

		}

		@Override
		public void mouseReleased(MouseEvent e) {

		}

		@Override
		public void mouseEntered(MouseEvent e) {

		}

		@Override
		public void mouseExited(MouseEvent e) {

		}

	}
	
	// Main method
	public static void main(String[] args) {
		TicTacToe ticTacToe = new TicTacToe();
	}
	
	

}
