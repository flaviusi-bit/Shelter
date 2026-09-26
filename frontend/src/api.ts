export type Animal={id:string;animalCode:string;name:string;animalType:string;sex:string;dateOfBirth?:string;weightKg?:number;microchipNumber?:string;intakeDate:string;rescueSource?:string;location?:string;status:string;notes?:string;photoUrl?:string}
export type AnimalInput=Omit<Animal,'id'|'animalCode'>
export type Treatment={id:string;medication:string;dose:string;route:string;frequency:string;startDate:string;endDate?:string;status:string;instructions?:string;prescribedBy?:string}
export type Administration={id:string;scheduledAt:string;administeredAt?:string;administeredBy?:string;status:'SCHEDULED'|'ADMINISTERED'|'MISSED'|'SKIPPED';notes?:string}
async function json(r:Response){if(r.status===401){localStorage.removeItem('shelterAuth');window.dispatchEvent(new Event('shelter-auth-required'));throw new Error('Authentication required')}if(!r.ok)throw new Error(await r.text());return r.json()}
function headers():Record<string,string>{const auth=localStorage.getItem('shelterAuth');return auth?{Authorization:'Basic '+auth}:{} }
export async function getAnimals(q=''):Promise<Animal[]>{return json(await fetch('/api/animals'+(q?'?q='+encodeURIComponent(q):''),{headers:headers()}))}
export async function createAnimal(a:AnimalInput):Promise<Animal>{return json(await fetch('/api/animals',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function updateAnimal(id:string,a:AnimalInput):Promise<Animal>{return json(await fetch('/api/animals/'+id,{method:'PUT',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function getTreatments(animalId:string):Promise<Treatment[]>{return json(await fetch('/api/animals/'+animalId+'/treatments',{headers:headers()}))}
export async function getAdministrations(animalId:string,treatmentId:string):Promise<Administration[]>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations',{headers:headers()}))}
export async function generateAdministrations(animalId:string,treatmentId:string,days=14):Promise<Administration[]>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/generate?days='+days,{method:'POST',headers:headers()}))}
export async function administer(animalId:string,treatmentId:string,administrationId:string,notes?:string):Promise<Administration>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/'+administrationId+'/administer',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify({notes})}))}
export async function setAdministrationStatus(animalId:string,treatmentId:string,administrationId:string,status:'MISSED'|'SKIPPED',notes?:string):Promise<Administration>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/'+administrationId+'/status',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify({status,notes})}))}

export type MedicalEvent={id:string;eventType:string;eventDate:string;title:string;diagnosis?:string;provider?:string;notes?:string}
export type Vaccination={id:string;vaccineName:string;vaccineType?:string;administeredDate:string;nextDueDate?:string;batchNumber?:string;veterinarian?:string;notes?:string}
export type Deworming={id:string;productName:string;treatmentType?:string;administeredDate:string;nextDueDate?:string;dose?:string;veterinarian?:string;notes?:string}
export async function getMedicalEvents(id:string):Promise<MedicalEvent[]>{return json(await fetch('/api/animals/'+id+'/medical-events',{headers:headers()}))}
export async function createMedicalEvent(id:string,a:Omit<MedicalEvent,'id'>):Promise<MedicalEvent>{return json(await fetch('/api/animals/'+id+'/medical-events',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function getVaccinations(id:string):Promise<Vaccination[]>{return json(await fetch('/api/animals/'+id+'/vaccinations',{headers:headers()}))}
export async function createVaccination(id:string,a:Omit<Vaccination,'id'>):Promise<Vaccination>{return json(await fetch('/api/animals/'+id+'/vaccinations',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function getDewormings(id:string):Promise<Deworming[]>{return json(await fetch('/api/animals/'+id+'/dewormings',{headers:headers()}))}
export async function createDeworming(id:string,a:Omit<Deworming,'id'>):Promise<Deworming>{return json(await fetch('/api/animals/'+id+'/dewormings',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}


export type MedicalDocument={id:string;documentType:string;title:string;fileUrl:string;documentDate?:string;notes?:string;uploadedBy?:string;createdAt?:string}
export async function getDocuments(id:string):Promise<MedicalDocument[]>{return json(await fetch('/api/animals/'+id+'/documents',{headers:headers()}))}
export async function createDocument(id:string,a:Omit<MedicalDocument,'id'|'uploadedBy'|'createdAt'>):Promise<MedicalDocument>{return json(await fetch('/api/animals/'+id+'/documents',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function uploadDocument(id:string,file:File,meta:{documentType:string;title:string;documentDate?:string;notes?:string}):Promise<MedicalDocument>{const form=new FormData();form.append('file',file);form.append('documentType',meta.documentType);form.append('title',meta.title);if(meta.documentDate)form.append('documentDate',meta.documentDate);if(meta.notes)form.append('notes',meta.notes);return json(await fetch('/api/animals/'+id+'/documents/upload',{method:'POST',headers:headers(),body:form}))}

export type Task={id:string;animal?:{id:string;name:string};taskType:string;title:string;dueAt:string;status:'OPEN'|'COMPLETED'|'SKIPPED';priority:'LOW'|'NORMAL'|'HIGH'|'URGENT';assignedTo?:string;notes?:string}
export async function getTasks(status='OPEN'):Promise<Task[]>{return json(await fetch('/api/tasks?status='+encodeURIComponent(status),{headers:headers()}))}
export async function createTask(a:Omit<Task,'id'|'status'>):Promise<Task>{return json(await fetch('/api/tasks',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function completeTask(id:string):Promise<Task>{return json(await fetch('/api/tasks/'+id+'/complete',{method:'POST',headers:headers()}))}
export async function skipTask(id:string):Promise<Task>{return json(await fetch('/api/tasks/'+id+'/skip',{method:'POST',headers:headers()}))}

export type BackupInfo={name:string;createdAt:string;databaseBytes:number;documentsBytes:number;checksumAvailable:boolean}
export type BackupVerification={valid:boolean;message:string}
export async function getBackups():Promise<BackupInfo[]>{return json(await fetch('/api/admin/backups',{headers:headers()}))}
export async function verifyBackup(name:string):Promise<BackupVerification>{return json(await fetch('/api/admin/backups/'+encodeURIComponent(name)+'/verify',{method:'POST',headers:headers()}))}

export type AuditLog={id:string;actor:string;action:string;entityType:string;entityId?:string;occurredAt:string;details?:string}
export async function getAuditLogs(limit=200):Promise<AuditLog[]>{return json(await fetch('/api/admin/audit?limit='+limit,{headers:headers()}))}
