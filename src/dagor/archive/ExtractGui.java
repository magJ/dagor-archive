package dagor.archive;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;


public class ExtractGui{

  public static void main(String[] args){
    EventQueue.invokeLater(new Runnable(){
      @Override
      public void run() {
        new ExtractGui().display();
      }
    });
  }
  
  JFrame frame;
  JTextField archiveField;
  JTextField destField;
  JLabel errorLbl;

  private void display(){
    String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
    try {
      UIManager.setLookAndFeel(lookAndFeel);
    } catch (Exception e) {

    }
    archiveField = new JTextField();
    destField = new JTextField();

    JButton caButton = new JButton("Open");
    JButton cdButton = new JButton("Open");
    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.setBorder(BorderFactory.createTitledBorder("Dagor Archive Extractor"));
    panel.add(new JLabel("Choose archive file:"));
    panel.add(archiveField);
    panel.add(caButton);
    panel.add(new JLabel("Choose output directory:"));
    panel.add(destField);
    panel.add(cdButton);

    JButton extractButton = new JButton("Extract");
    panel.add(extractButton);
    errorLbl = new JLabel();
    panel.add(errorLbl);
    
    caButton.addActionListener(new ChooseArchive());
    cdButton.addActionListener(new ChooseDestination());
    extractButton.addActionListener(new Extract());

    frame = new JFrame("Dagor Archive Extractor");
    frame.setMinimumSize(new Dimension(300,300));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(panel);
    frame.pack();
    frame.setVisible(true);
  }

  class ChooseArchive implements ActionListener{
    @Override
    public void actionPerformed(ActionEvent event) {
      JFileChooser c = new JFileChooser();
      String warTDir = System.getenv("ProgramFiles");
      warTDir += " (x86)\\WarThunder";
      File defaultDir = new File(warTDir);
      if(defaultDir.exists()){
        c.setCurrentDirectory(defaultDir);
      }
      int rVal = c.showOpenDialog(frame);
      if (rVal == JFileChooser.APPROVE_OPTION) {
        archiveField.setText(c.getSelectedFile().getAbsolutePath());
      }
      if (rVal == JFileChooser.CANCEL_OPTION) {
        
      }
    }
  }
  
  class ChooseDestination implements ActionListener{
    @Override
    public void actionPerformed(ActionEvent event) {
      JFileChooser c = new JFileChooser();
      c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      int rVal = c.showOpenDialog(frame);
      if (rVal == JFileChooser.APPROVE_OPTION) {
        destField.setText(c.getSelectedFile().getAbsolutePath());
      }
      if (rVal == JFileChooser.CANCEL_OPTION) {
        
      }
    }
  }
  
  class Extract implements ActionListener{
    @Override
    public void actionPerformed(ActionEvent arg0) {
      try {
        DagorArchiveReader dar = new DagorArchiveReader(new File(archiveField.getText()));
        dar.extractAll(new File(destField.getText()));
        errorLbl.setText(dar.getFileNames().length + " file(s) extacted.");
      } catch (IOException e) {
        errorLbl.setText("Error: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

}
