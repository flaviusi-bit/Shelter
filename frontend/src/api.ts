export type Animal={id:string;name:string;animalType:string;sex:string;dateOfBirth?:string;weightKg?:number;microchipNumber?:string;intakeDate:string;rescueSource?:string;location?:string;status:string;notes?:string}
export type Treatment={id:string;medication:string;dose:string;route:string;frequency:string;startDate:string;endDate?:string;status:string;instructions?:string;prescribedBy?:string}
export type Administration={id:string;scheduledAt:string;administeredAt?:string;administeredBy?:string;status:'SCHEDULED'|'ADMINISTERED'|'MISSED'|'SKIPPED';notes?:string}
async function json(r:Response){if(!r.ok)throw new Error(await r.text());return r.json()}
export async function getAnimals(q=''):Promise<Animal[]>{return json(await fetch('/api/animals'+(q?'?q='+encodeURIComponent(q):'')))}
export async function createAnimal(a:Omit<Animal,'id'>):Promise<Animal>{return json(await fetch('/api/animals',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(a)}))}
export async function getTreatments(animalId:string):Promise<Treatment[]>{return json(await fetch('/api/animals/'+animalId+'/treatments'))}
export async function getAdministrations(animalId:string,treatmentId:string):Promise<Administration[]>{
 return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations'))
}
export async function generateAdministrations(animalId:string,treatmentId:string,days=14):Promise<Administration[]>{
 return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/generate?days='+days,{method:'POST'}))
}
export async function administer(animalId:string,treatmentId:string,administrationId:string,administeredBy:string,notes?:string):Promise<Administration>{
 return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/'+administrationId+'/administer',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({administeredBy,notes})}))
}
export async function setAdministrationStatus(animalId:string,treatmentId:string,administrationId:string,status:'MISSED'|'SKIPPED',notes?:string):Promise<Administration>{
 return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/'+administrationId+'/status',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({status,notes})}))
}
