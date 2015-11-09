import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import org.jasypt.util.text.BasicTextEncryptor;

import java.util.Date;

public final class CraigslistNotifier {
	private static ArrayList<Ad> ads = new ArrayList<Ad>(), newAds = new ArrayList<Ad>();
	private static ArrayList<String> searchTerms = new ArrayList<String>(), cities = new ArrayList<String>(), negativeKeywords = new ArrayList<String>();
	private static String email, password, recipient;
	private static int frequency;
	private static JFrame f = new JFrame("Settings");
	private static boolean firstTime, settingsChanged;

	public static void main(String[] args) {
		createSystemTrayIcon();
		loadSettings();
		constructSettingsGUI();
		loadAds();
		if (firstTime) {
			f.setVisible(true);
			JOptionPane.showMessageDialog(null, "No saved settings found. Please enter your settings.\nYou can view more info by hovering over labels.\nChanges to settings will take effect when the window is closed.\nNote that making more than 1 request every\n~3 minutes may result in an automatic IP ban by Craigslist.\n\nAccess various options by right-clicking the icon in the system tray.");
			Object lock = new Object();
			Thread t = new Thread() {
				public void run() {
					synchronized(lock) {
						while (f.isVisible()) {
							try {
								lock.wait();
							}
							catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						loadSettings();
						
					}
				}
			};
			t.start();
			f.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					synchronized (lock) {
						f.setVisible(false);
						lock.notify();
					}
				}

			});
			try {
				t.join();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		while (true) {
			settingsChanged = false;
			start:
			for (int c = 0; c < cities.size(); c++) {
				if (settingsChanged)
					break start;
				for (int t = 0; t < searchTerms.size(); t++) {
					if (settingsChanged)
						break start;
					System.out.println(cities.get(c) + " " + searchTerms.get(t));
					updateAds(cities.get(c), searchTerms.get(t));
					for (Ad ad : newAds)
						sendEmail(ad.getTitle(), "$" + ad.getPrice() + " in " + ad.getLocation() + "\n" + ad.getLink());
					saveAds();
					try {
						Thread.sleep((long)(((frequency + Math.random()) * 60000) / (searchTerms.size() * cities.size())));
					}
					catch (InterruptedException e) {
						System.out.println("InterruptedException");
					}
					if (settingsChanged)
						break start;
				}
				if (settingsChanged)
					break start;
			}
		}
	}

	public static void constructSettingsGUI() {
		String sCities = "", sTerms = "", sNegs = "";
		BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
		textEncryptor.setPassword("Nick");
		Container p = f.getContentPane();
		f.setLayout(null);
		JLabel lEmailInstructions = new JLabel("Gmail account to send emails from");
		JLabel lEmail = new JLabel("address:");
		JLabel lPassword = new JLabel("password:");
		JLabel lLink = new JLabel("<html>You must enable less secure apps<br>to access your account; do so <a href=\"\">here</a></html>");
		lLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		lLink.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				try {
					Desktop.getDesktop().browse(new URI("https://www.google.com/settings/security/lesssecureapps"));
				}
				catch (IOException i) {
					System.out.println("IOException while opening webpage");
				}
				catch (URISyntaxException u) {
					System.out.println("URISyntaxException");
				}
			}
		});
		JLabel lRecipient = new JLabel("recipient:");
		JLabel lCities = new JLabel("cities to search:");
		JLabel lTerms = new JLabel("search terms:");
		JLabel lNeg = new JLabel("negative keywords:");
		JLabel lRefresh = new JLabel("refresh search results every               minutes");
		JLabel lRequests = new JLabel("<html>You are currently making 1<br>request every " + (searchTerms.size() * cities.size()/(double)frequency) + " minutes</html>");
		//load text fields
		for (int i = 0; i < cities.size(); i++) {
			sCities += cities.get(i);
			if (i < cities.size() - 1)
				sCities += ", ";
		}
		for (int i = 0; i < searchTerms.size(); i++) {
			sTerms += searchTerms.get(i);
			if (i < searchTerms.size() - 1)
				sTerms += ", ";
		}
		for (int i = 0; i < negativeKeywords.size(); i++) {
			sNegs += negativeKeywords.get(i);
			if (i < negativeKeywords.size() - 1)
				sNegs += ", ";
		}
		JTextField tEmail = new JTextField(CraigslistNotifier.email, 13);
		JTextField tRecipient = new JTextField(recipient, 13);
		JTextField tCities = new JTextField(sCities, 9);
		JTextField tTerms = new JTextField(sTerms, 28);
		JTextField tNeg = new JTextField(sNegs, 25);
		JTextField tRefresh = new JTextField(Integer.toString(frequency), 3);
		JPasswordField tPassword = new JPasswordField(password, 12);
		lEmail.setToolTipText("Gmail address to send emails from");
		lLink.setToolTipText("https://www.google.com/settings/security/lesssecureapps");
		lRecipient.setToolTipText("Email address to send emails to; doesn't have to be a Gmail address");
		lCities.setToolTipText("ensure <city>.craigslist.org is a valid url; separate multiple entries with a comma and space");
		lTerms.setToolTipText("separate multiple entries with a comma and space");
		lNeg.setToolTipText("if an ad's title contains any negative keyword, you will not be notified of it; separate multiple entries with a comma and space");
		lRequests.setToolTipText("Making more than 1 request every ~3 minutes may result in an automatic IP ban from Craigslist");
		lPassword.setToolTipText("password for the Gmail account that emails are sent from; it is encrypted before being stored, and is used for nothing else than sending emails");
		JButton bDonate = new JButton(new ImageIcon(CraigslistNotifier.class.getResource("/donateButton.png")));
		bDonate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(Desktop.isDesktopSupported()) {
					try {
						Desktop.getDesktop().browse(new URI("https://www.paypal.me/NicholasVanDyke"));
					}
					catch (IOException i) {
						System.out.println("IOException while opening webpage");
					}
					catch (URISyntaxException u) {
						System.out.println("URISyntaxException while opening webpage");
					}
				}
			}
		});
		//add elements to container
		p.add(lEmailInstructions);
		p.add(lEmail);
		p.add(lPassword);
		p.add(lLink);
		p.add(lRecipient);
		p.add(lCities);
		p.add(lTerms);
		p.add(lNeg);
		p.add(lRefresh);
		p.add(lRequests);
		p.add(tEmail);
		p.add(tPassword);
		p.add(tRecipient);
		p.add(tCities);
		p.add(tTerms);
		p.add(tNeg);
		p.add(tRefresh);
		p.add(bDonate);
		Insets insets = f.getInsets();
		//position labels
		Dimension size = lEmailInstructions.getPreferredSize();
		lEmailInstructions.setBounds(5 + insets.left, 1 + insets.top, 300, size.height);
		size = lEmail.getPreferredSize();
		lEmail.setBounds(5 + insets.left, 22 + insets.top, size.width, size.height);
		size = lPassword.getPreferredSize();
		lPassword.setBounds(5 + insets.left, 43 + insets.top, size.width, size.height);
		size = lLink.getPreferredSize();
		lLink.setBounds(5 + insets.left, 62 + insets.top, size.width, size.height);
		size = lRecipient.getPreferredSize();
		lRecipient.setBounds(5 + insets.left, 101 + insets.top, size.width, size.height);
		size = lCities.getPreferredSize();
		lCities.setBounds(5 + insets.left, 122 + insets.top, size.width, size.height);
		size = lTerms.getPreferredSize();
		lTerms.setBounds(5 + insets.left, 143 + insets.top, size.width, size.height);
		size = lNeg.getPreferredSize();
		lNeg.setBounds(5 + insets.left, 164 + insets.top, size.width, size.height);
		size = lRefresh.getPreferredSize();
		lRefresh.setBounds(5 + insets.left, 185 + insets.top, size.width, size.height);
		size = lRequests.getPreferredSize();
		lRequests.setBounds(250 + insets.left, 1 + insets.top, size.width, size.height);
		//position text boxes
		size = tEmail.getPreferredSize();
		tEmail.setBounds(59 + insets.left, 20 + insets.top, size.width, size.height);
		size = tPassword.getPreferredSize();
		tPassword.setBounds(70 + insets.left, 41 + insets.top, size.width, size.height);
		size = tRecipient.getPreferredSize();
		tRecipient.setBounds(59 + insets.left, 99 + insets.top, size.width, size.height);
		size = tCities.getPreferredSize();
		tCities.setBounds(103 + insets.left, 120 + insets.top, size.width, size.height);
		size = tTerms.getPreferredSize();
		tTerms.setBounds(89 + insets.left, 141 + insets.top, size.width, size.height);
		size = tNeg.getPreferredSize();
		tNeg.setBounds(122 + insets.left, 162 + insets.top, size.width, size.height);
		size = tRefresh.getPreferredSize();
		tRefresh.setBounds(172 + insets.left, 183 + insets.top, size.width, size.height);
		//position buttons
		size = tRefresh.getPreferredSize();
		bDonate.setBounds(280 + insets.left, 183 + insets.top, 96, 21);
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent windowEvent) {
				String n = System.getProperty("line.separator");
				try {
					BufferedWriter w = new BufferedWriter(new FileWriter(new File("settings.txt")));
					w.write(tEmail.getText() + n);
					w.write(textEncryptor.encrypt(tPassword.getText()) + n);
					w.write(tRecipient.getText() + n);
					w.write(tCities.getText() + n);
					w.write(tTerms.getText() + n);
					w.write(tNeg.getText() + n);
					w.write(tRefresh.getText());
					w.close();
				}
				catch (IOException e) {
					System.out.println("Error writing settings to file");
				}
				loadSettings();
				lRequests.setText("<html>You are currently making 1<br>request every " + (searchTerms.size() * cities.size()/(double)frequency) + " minutes</html>");
			}
		});
		f.setSize(414, 236);
		f.setResizable(false);
		f.setLocationRelativeTo(null);
	}

	public static void loadSettings() {
		ArrayList<String> st = new ArrayList<String>(searchTerms), c = new ArrayList<String>(cities), nk = new ArrayList<String>(negativeKeywords);
		String text;
		Scanner lines = null, tokens = null;
		searchTerms.clear();
		cities.clear();
		negativeKeywords.clear();
		BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
		textEncryptor.setPassword("Nick");
		try {
			lines = new Scanner(new File("settings.txt"));
		}
		catch (FileNotFoundException e) {
			try {
				BufferedWriter w = new BufferedWriter(new FileWriter(new File("settings.txt")));
				String n = System.getProperty("line.separator");
				w.write("example@gmail.com" + n + n);
				w.write("example@gmail.com" + n + n + n + n);
				w.write("20");
				w.close();
				lines = new Scanner(new File("settings.txt"));
			}
			catch (IOException i) {
				System.out.println("IOException creating settings.txt");
			}
			firstTime = true;
		}
		email = lines.nextLine();
		text = lines.nextLine();
		if (text.length() > 0)
			password = textEncryptor.decrypt(text);
		else
			password = "";
		recipient = lines.nextLine();
		tokens = new Scanner(lines.nextLine());
		tokens.useDelimiter(", ");
		while (tokens.hasNext()) {
			text = tokens.next();
			if (!cities.contains(text))
				cities.add(text);
		}
		tokens.close();
		tokens = new Scanner(lines.nextLine());
		tokens.useDelimiter(", ");
		while (tokens.hasNext()) {
			text = tokens.next();
			if (!searchTerms.contains(text))
				searchTerms.add(text);
		}
		tokens.close();
		tokens = new Scanner(lines.nextLine());
		tokens.useDelimiter(", ");
		while (tokens.hasNext()) {
			text = tokens.next();
			if (!negativeKeywords.contains(text))
				negativeKeywords.add(text);
		}
		tokens.close();
		frequency = Integer.parseInt(lines.nextLine());
		lines.close();
		if (!st.equals(searchTerms) || !c.equals(cities) || !nk.equals(negativeKeywords))
			settingsChanged = true;
	}

	public static void createSystemTrayIcon() {
		if (!SystemTray.isSupported()) {
			System.out.println("SystemTray is not supported");
			return;
		}
		TrayIcon trayIcon = null;
		final PopupMenu popup = new PopupMenu();
		final SystemTray tray = SystemTray.getSystemTray();
		trayIcon = new TrayIcon(f.getToolkit().getImage(CraigslistNotifier.class.getResource("/trayIcon.png")), "Nick's Notifier");
		MenuItem settingsItem = new MenuItem("Settings");
		MenuItem exitItem = new MenuItem("Exit");
		popup.add(settingsItem);
		popup.add(exitItem);
		trayIcon.setImageAutoSize(true);
		trayIcon.setPopupMenu(popup);
		try {
			tray.add(trayIcon);
		}
		catch (AWTException e) {
			System.out.println("TrayIcon could not be added");
		}
		settingsItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				f.setVisible(true);
			}
		});
		exitItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteOldAds();
				new File("savedAds.txt").delete();
				saveAds();
				System.exit(0);
			}
		});
	}

	public static void updateAds(String city, String term) {
		String html = "";
		boolean skip, add;
		newAds.clear();
		try {
			html = Scraper.getHtml("http://" + city + ".craigslist.org/search/sss?sort=date&query=" + term.replace(" ", "%20"));
		}
		catch (IOException e) {
			sendEmail("IP blocked", "rip");
			System.out.print("ip blocked");
			System.exit(0);
		}
		for (Ad temp : createAds(html)) {
			//System.out.println(temp);
			skip = false;
			add = true;
			if (ads.isEmpty()) {
				ads.add(temp);
				newAds.add(temp);
				System.out.println("added: " + temp);
			}
			for (String word : negativeKeywords)
				if (skip == false && temp.getTitle().toLowerCase().contains(word.toLowerCase())) {
					skip = true;
					add = false;
					System.out.println("neg: " + temp);
				}
			if (!skip)
				for (Ad ad : ads)
					if (temp.equals(ad)) {
						add = false;
						System.out.println("already seen: " + temp);
					}
			if (add) {
				ads.add(temp);
				newAds.add(temp);
				System.out.println("added: " + temp);
			}
		}
	}

	//given the html code for a Craigslist page, creates and returns an ArrayList containing Ads created from the html code
	public static ArrayList<Ad> createAds(String str) {
		ArrayList<Ad> result = new ArrayList<Ad>();
		if (str.contains("noresults")) {
			System.out.println("no results");
			return result;
		}
		Ad temp;
		String title, date, location = "n/a", link, city;
		String[] splitHtml;
		int price;
		city = str.substring(str.indexOf("<option value=\"") + 15, str.indexOf("</option>"));
		city = city.substring(0, city.indexOf("\">"));
		if (str.contains("<h4"))
			splitHtml =  str.substring(str.indexOf("<p"), str.indexOf("<h4")).split("</p>");
		else splitHtml = str.substring(str.indexOf("<p"), str.indexOf("<div id=\"mapcontainer")).split("</p>");
		for (String adHtml : splitHtml) {
			title = adHtml.substring(adHtml.indexOf("hdrlnk") + 8, adHtml.indexOf("</a> </span>")).replace("&amp;", "&");
			date = adHtml.substring(adHtml.indexOf("title") + 7, adHtml.indexOf("title") + 29);
			if (adHtml.contains("<small>"))
				location = adHtml.substring(adHtml.indexOf("<small>") + 9, adHtml.indexOf("</small>") - 1);
			link = "https://" + city + ".craigslist.org" + adHtml.substring(adHtml.indexOf("href") + 6, adHtml.indexOf("html") + 4);
			if (adHtml.contains("price"))
				price = Integer.parseInt(adHtml.substring(adHtml.indexOf("price") + 8, adHtml.indexOf("</span")));
			else price = 0;
			temp = new Ad(title, price, date, location, link);
			result.add(temp);
		}
		return result;
	}

	//deletes the ad if it's date is 30 days older than the current date
	public static void deleteOldAds() {
		long cutoff = new Date().getTime() - 2592000000L;
		for (int i = 0; i < ads.size(); i++)
			if (ads.get(i).getDate().getTime() <  cutoff) {
				ads.remove(i);
				i--;
			}
	}

	public static void loadAds() {
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream("savedAds.txt"));
			boolean end = false;
			while (!end) {
				try {
					ads.add((Ad)ois.readObject());
				}
				catch (IOException e) {
					end = true;
				}
				catch (ClassNotFoundException e) {
					System.out.println("ClassNotFoundException");
				}
			}
			ois.close();
		}
		catch (IOException e) {
			System.out.println("IOException while loading ads");
		}
	}

	public static void saveAds() {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("savedAds.txt"));
			for (Ad ad : ads)
				oos.writeObject(ad);
			oos.close();
		}
		catch (IOException e) {
			System.out.println("IOException while saving");
		}
	}

	public static void sendEmail(String subject, String body) {
		try {
			GoogleMail.Send(email, password, recipient, subject, body);
		}
		catch (AddressException e) {
		}
		catch (MessagingException e) {
		}
	}
}