//
//  PrivacyPolicyController.swift
//  HumanToHuman
//
//  Created by Albert Liu on 6/22/20.
//  Copyright © 2020 Albert Liu. All rights reserved.
//

import Foundation
import UIKit

class PrivacyPolicyController: UIViewController {

    @IBOutlet var privacyPolicy: UITextView!
    @IBOutlet var privacyPolicySwitch: UISwitch!
    
    override func viewDidLoad() {
        print("privacy policy controller loading...")
        privacyPolicy.isEditable = false
        privacyPolicy.attributedText = AppLogic.getPolicy()
        
        switch AppLogic.getAppState() {
        case APPSTATE_NO_EXPERIMENT:
            print("why did this happen?")
            exit(1)
            break
        case APPSTATE_LOGGING_IN:
            print("why did this happen?")
            exit(1)
            break
        case APPSTATE_EXPERIMENT_JOINED_NOT_ACCEPTED_NOT_RUNNING:
            privacyPolicySwitch.isOn = false
            break
        default:
            privacyPolicySwitch.isOn = true
        }
    }
    
    @IBAction func goBack() {
        guard AppLogic.getAppState() == APPSTATE_EXPERIMENT_JOINED_NOT_ACCEPTED_NOT_RUNNING else {
            self.dismiss(animated: true, completion: nil)
            return
        }
        
        let refreshAlert = UIAlertController(title: "Ignore Consent Form",
                                             message: "You'll be kicked from the experiment.",
                                             preferredStyle: UIAlertController.Style.alert)
        
        refreshAlert.addAction(UIAlertAction(title: "Leave", style: .default, handler: { (action: UIAlertAction!) in
            AppLogic.ignorePrivacyPolicy()
            DispatchQueue.main.async {
                self.dismiss(animated: true, completion: nil)
            }
        }))

        refreshAlert.addAction(UIAlertAction(title: "Cancel", style: .cancel, handler: { (action: UIAlertAction!) in }))

        present(refreshAlert, animated: true, completion: nil)
    }
    
    @IBAction func togglePrivacyPolicy() {
        if self.privacyPolicySwitch.isOn {
            AppLogic.acceptPrivacyPolicy() { errorString in
                DispatchQueue.main.async {
                    if let errorString = errorString {
                        print(errorString)
                        self.privacyPolicySwitch.isOn = false
                        self.dismiss(animated: true, completion: nil)
                        return
                    }
                    
                    self.privacyPolicySwitch.isEnabled = false
                    self.privacyPolicySwitch.isOn = true
                    self.dismiss(animated: true, completion: nil)
                }
            }
        } else {
            let refreshAlert = UIAlertController(title: "Revoke Consent Form",
                                                 message: "You'll be kicked from the experiment.",
                                                 preferredStyle: UIAlertController.Style.alert)
            
            refreshAlert.addAction(UIAlertAction(title: "Leave", style: .default, handler: { (action: UIAlertAction!) in
                AppLogic.leaveExperiment() { str in
                    DispatchQueue.main.async {
                        if let str = str {
                            print("Exiting experiment failed (\(str))")
                            self.privacyPolicySwitch.isOn = true
                        } else {
                            self.dismiss(animated: true, completion: nil)
                        }
                    }
                }
            }))

            refreshAlert.addAction(UIAlertAction(title: "Cancel", style: .cancel, handler: { (action: UIAlertAction!) in
                self.privacyPolicySwitch.isOn = true
            }))

            present(refreshAlert, animated: true, completion: nil)
        }
    }
}

